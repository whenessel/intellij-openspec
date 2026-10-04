package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Direct executable launch only. Environment values remain private and are never logged. */
public final class CodexProcessPolicy {
    private CodexProcessPolicy() {}
    // Preserve CLI-owned login/config and explicitly reported API/proxy/TLS modes, without interpreter injection.
    private static final Set<String> ENVIRONMENT = Set.of("PATH", "HOME", "USER", "LOGNAME", "SHELL", "TMPDIR", "TMP", "TEMP", "LANG", "LC_ALL", "LC_CTYPE", "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_CACHE_HOME", "XDG_RUNTIME_DIR", "CODEX_HOME", "OPENAI_API_KEY", "CODEX_API_KEY", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "all_proxy", "no_proxy", "SSL_CERT_FILE", "SSL_CERT_DIR", "CURL_CA_BUNDLE", "NODE_EXTRA_CA_CERTS");
    public static Map<String,String> environment(Map<String,String> inherited) {
        Map<String,String> result = new HashMap<>();
        inherited.forEach((key,value) -> { if (ENVIRONMENT.contains(key)) result.put(key,value); });
        return Map.copyOf(result);
    }
    public static ProcessBuilder builder(List<String> arguments,Path root,Map<String,String> inherited) throws AiApiException {
        if(arguments.isEmpty())throw new AiApiException("Missing Codex executable argument.");
        List<String> resolved=new ArrayList<>(arguments);
        resolved.set(0,resolve(arguments.get(0),inherited).toString());
        ProcessBuilder builder=new ProcessBuilder(resolved).directory(root.toFile());
        builder.environment().clear();builder.environment().putAll(environment(inherited));
        return builder;
    }
    public static String validate(String value) throws AiApiException {
        if (value == null || value.isBlank() || value.chars().anyMatch(c -> c == 0 || c == '\n' || c == '\r')) throw new AiApiException("Codex executable must be a path or command name, without control characters.");
        final String name;
        try { name = Path.of(value).getFileName().toString().toLowerCase(Locale.ROOT); }
        catch (InvalidPathException | NullPointerException e) { throw new AiApiException("Codex executable path is invalid."); }
        if (Set.of("sh", "bash", "zsh", "fish", "cmd", "cmd.exe", "powershell", "pwsh", "node", "npm", "npx", "python", "python3").contains(name) || name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".ps1")) throw new AiApiException("Shell/interpreter wrappers are unsupported; select the installed Codex executable.");
        return value;
    }
    public static Path resolve(String executable, Map<String,String> inherited) throws AiApiException {
        validate(executable);
        Path path = Path.of(executable);
        if (path.isAbsolute() || executable.contains("/") || executable.contains("\\")) {
            if (!path.isAbsolute()) throw new AiApiException("Codex executable paths must be absolute; a command name can use PATH.");
            return requireExecutable(path);
        }
        for (String directory : inherited.getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (directory.isBlank()) continue; // Never search the working/project directory implicitly.
            Path parent = Path.of(directory);
            if (!parent.isAbsolute()) continue;
            Path candidate = parent.resolve(path);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate.toAbsolutePath().normalize();
        }
        throw new AiApiException("Cannot resolve the installed Codex executable on PATH; configure an absolute executable path.");
    }
    private static Path requireExecutable(Path path) throws AiApiException {
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) throw new AiApiException("Configured Codex executable is missing or not executable.");
        return path.toAbsolutePath().normalize();
    }
}
