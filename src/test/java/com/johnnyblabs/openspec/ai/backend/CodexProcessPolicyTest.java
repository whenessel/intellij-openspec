package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CodexProcessPolicyTest {
    @TempDir Path directory;
    @Test void spacesUnicodeAndMetacharactersAreLiteralExecutablePathCharacters() throws Exception {
        Path executable=directory.resolve("Codex путь $(touch forbidden); `literal`");
        Files.writeString(executable,"fixture");assertTrue(executable.toFile().setExecutable(true));
        assertEquals(executable,CodexProcessPolicy.resolve(executable.toString(),Map.of()));
        assertFalse(Files.exists(directory.resolve("forbidden")));
        List<String> arguments=new CodexAppServerBackend(executable.toString(),(args,root)->{throw new AssertionError();}).arguments(directory);
        assertEquals(executable.toString(),arguments.get(0));assertEquals(List.of("app-server","--listen","stdio://"),arguments.subList(1,4));
    }
    @Test void realDisposableExecutableReceivesLiteralArgumentsAndControlledEnvironment() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Path executable=directory.resolve("Codex тест $(touch forbidden); `literal`");
        Files.writeString(executable,"#!/bin/sh\nprintf '%s\\n' \"$1\" \"$HOME\" \"$CODEX_HOME\" \"${NODE_OPTIONS-unset}\" \"${UNRELATED_SECRET-unset}\"\n");
        assertTrue(executable.toFile().setExecutable(true));
        String argument="$(touch forbidden) ; текст `literal`";
        ProcessBuilder builder=CodexProcessPolicy.builder(List.of(executable.toString(),argument),directory,
                Map.of("HOME","fixture-home","CODEX_HOME","fixture-config","NODE_OPTIONS","injected","UNRELATED_SECRET","discarded"));
        Process process=builder.start();
        assertTrue(process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(argument+"\nfixture-home\nfixture-config\nunset\nunset\n",output);
        assertFalse(Files.exists(directory.resolve("forbidden")));
    }
    @Test void commandResolutionUsesOnlyAbsolutePathEntriesAndRejectsMissingOrWrappers() throws Exception {
        Path executable=directory.resolve("codex");Files.writeString(executable,"fixture");assertTrue(executable.toFile().setExecutable(true));
        assertEquals(executable,CodexProcessPolicy.resolve("codex",Map.of("PATH",".:relative:"+directory)));
        for(String name:List.of("bash","node","npx","codex.cmd","codex.ps1","bad\u0000path","bad\npath","./codex"))
            assertThrows(AiApiException.class,()->CodexProcessPolicy.resolve(name,Map.of("PATH",directory.toString())),name);
        assertThrows(AiApiException.class,()->CodexProcessPolicy.resolve(directory.resolve("missing").toString(),Map.of()));
        assertThrows(AiApiException.class,()->CodexProcessPolicy.resolve("codex",Map.of("PATH",".:relative")));
    }
    @Test void environmentPreservesCliAuthLocationsButDropsInterpreterAndDestinationInjection() {
        Map<String,String> inherited=new HashMap<>(Map.of("HOME","private-home","PATH","/usr/bin","CODEX_HOME","private-config","OPENAI_API_KEY","fixture-not-secret","NODE_OPTIONS","--require malicious","LD_PRELOAD","malicious","OPENAI_BASE_URL","https://unreviewed.invalid","UNRELATED_SECRET","not-needed"));
        Map<String,String> controlled=CodexProcessPolicy.environment(inherited);
        assertEquals(Set.of("HOME","PATH","CODEX_HOME","OPENAI_API_KEY"),controlled.keySet());
        assertEquals("private-config",controlled.get("CODEX_HOME"));
        assertThrows(UnsupportedOperationException.class,()->controlled.put("LD_PRELOAD","malicious"));
        assertEquals(8,inherited.size());
    }
}
