package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExplorePromptServiceTest {

    @Mock Project project;
    @Mock ExploreContextService contextService;

    private ExplorePromptService service;

    @BeforeEach
    void setUp() {
        service = new ExplorePromptService(project);
    }

    @Nested
    class SkillFileLoading {

        @TempDir
        Path tempDir;

        @Test
        void skillsEraPathWinsOverLegacyCommandPath() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());

            // Skills-era file (1.5+ layout) with 1.6-style stamped frontmatter.
            Path skillsDir = tempDir.resolve(".claude/skills/openspec-explore");
            Files.createDirectories(skillsDir);
            Files.writeString(skillsDir.resolve("SKILL.md"),
                    "---\nname: openspec-explore\nallowed-tools: Bash(openspec:*)\ngeneratedBy: 1.6.0\n---\nSkills-era explore instructions.");
            // Legacy command file also present — must lose to the skills-era file.
            Path legacyDir = tempDir.resolve(".claude/commands/opsx");
            Files.createDirectories(legacyDir);
            Files.writeString(legacyDir.resolve("explore.md"), "Legacy explore instructions.");

            String instructions = service.loadSkillInstructions();

            assertEquals("Skills-era explore instructions.", instructions);
        }

        @Test
        void loadsClaudeSkillFile() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());

            Path skillDir = tempDir.resolve(".claude/commands/opsx");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("explore.md"),
                    "---\nname: Explore\n---\nCustom explore instructions here.");

            String instructions = service.loadSkillInstructions();

            assertEquals("Custom explore instructions here.", instructions);
        }

        @Test
        void loadsAugmentSkillFile_whenClaudeFileMissing() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());

            Path skillDir = tempDir.resolve(".augment/commands");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("opsx-explore.md"),
                    "Augment explore instructions.");

            String instructions = service.loadSkillInstructions();

            assertEquals("Augment explore instructions.", instructions);
        }

        @Test
        void fallsBackToDefault_whenNoSkillFilesExist() {
            when(project.getBasePath()).thenReturn(tempDir.toString());

            String instructions = service.loadSkillInstructions();

            assertEquals(ExplorePromptService.DEFAULT_EXPLORE_PROMPT, instructions);
        }

        @Test
        void fallsBackToDefault_whenBasePathIsNull() {
            when(project.getBasePath()).thenReturn(null);

            String instructions = service.loadSkillInstructions();

            assertEquals(ExplorePromptService.DEFAULT_EXPLORE_PROMPT, instructions);
        }

        @Test
        void oversizedSkillIsOmittedVisiblyWithoutReadingItsInstructions() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());
            Path directory = tempDir.resolve(".claude/skills/openspec-explore");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("SKILL.md"), "s".repeat(ExplorePromptService.MAX_SKILL_BYTES + 1));
            String instructions = service.loadSkillInstructions();
            assertTrue(instructions.startsWith(ExplorePromptService.DEFAULT_EXPLORE_PROMPT));
            assertTrue(instructions.contains("Context omission"));
            assertFalse(instructions.contains("ssssssssssss"));
        }

        @Test
        void skillAtByteLimitIsAccepted() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());
            Path directory = tempDir.resolve(".claude/skills/openspec-explore");
            Files.createDirectories(directory);
            String content = "s".repeat(ExplorePromptService.MAX_SKILL_BYTES);
            Files.writeString(directory.resolve("SKILL.md"), content);
            assertEquals(content, service.loadSkillInstructions());
        }

        @Test
        void symlinkedSkillAncestorIsRejected() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());
            Path external = Files.createDirectories(tempDir.resolve("outside/skills/openspec-explore"));
            Files.writeString(external.resolve("SKILL.md"), "outside instructions must not be loaded");
            try { Files.createSymbolicLink(tempDir.resolve(".claude"), tempDir.resolve("outside")); }
            catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symlinks unavailable on this test host");
            }
            String instructions = service.loadSkillInstructions();
            assertTrue(instructions.contains("Context omission"));
            assertFalse(instructions.contains("outside instructions"));
        }

        @Test
        void symlinkedSkillFileIsRejected() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());
            Path outside = tempDir.resolve("outside.md");
            Files.writeString(outside, "outside instructions");
            Path directory = Files.createDirectories(tempDir.resolve(".claude/skills/openspec-explore"));
            try { Files.createSymbolicLink(directory.resolve("SKILL.md"), outside); }
            catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symlinks unavailable on this test host");
            }
            assertTrue(service.loadSkillInstructions().contains("Context omission"));
        }

        @Test
        void stripsYamlFrontmatter() throws IOException {
            when(project.getBasePath()).thenReturn(tempDir.toString());

            Path skillDir = tempDir.resolve(".claude/commands/opsx");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("explore.md"),
                    "---\nname: Explore\ntags: [workflow]\n---\nThe actual content.");

            String instructions = service.loadSkillInstructions();

            assertEquals("The actual content.", instructions);
            assertFalse(instructions.contains("name:"));
            assertFalse(instructions.contains("tags:"));
        }
    }

    @Nested
    class PromptAssembly {

        @Test
        void includesThreeSections() {
            when(project.getBasePath()).thenReturn("/nonexistent");
            when(project.getService(ExploreContextService.class)).thenReturn(contextService);
            when(contextService.assembleContext()).thenReturn("# Project Context\n\nSome context.");

            String prompt = service.buildPrompt("How should we handle auth?");

            // Three sections separated by ---
            assertTrue(prompt.contains("---"));
            // Contains explore instructions (default since no skill file)
            assertTrue(prompt.contains("Enter explore mode"));
            // Contains project context
            assertTrue(prompt.contains("# Project Context"));
            assertTrue(prompt.contains("Some context."));
            // Contains topic
            assertTrue(prompt.contains("**Topic:** How should we handle auth?"));
        }

        @Test
        void topicChangesKeepScopeAndContextEditsInvalidateIt() {
            when(project.getBasePath()).thenReturn("/nonexistent");
            when(project.getService(ExploreContextService.class)).thenReturn(contextService);
            when(contextService.assembleContext()).thenReturn("context", "context", "changed context");
            var first = service.buildRequest("first topic");
            var second = service.buildRequest("follow up");
            var changed = service.buildRequest("follow up");
            assertEquals(first.contextScope(), second.contextScope());
            assertNotEquals(first.prompt(), second.prompt());
            assertNotEquals(second.contextScope(), changed.contextScope());
            verify(contextService, times(3)).assembleContext();
        }

        @Test
        void blankTopicUsesDefaultText() {
            when(project.getBasePath()).thenReturn("/nonexistent");
            when(project.getService(ExploreContextService.class)).thenReturn(contextService);
            when(contextService.assembleContext()).thenReturn("context");

            String prompt = service.buildPrompt("");

            assertTrue(prompt.contains("**Topic:** Open exploration"));
        }

        @Test
        void nullTopicUsesDefaultText() {
            when(project.getBasePath()).thenReturn("/nonexistent");
            when(project.getService(ExploreContextService.class)).thenReturn(contextService);
            when(contextService.assembleContext()).thenReturn("context");

            String prompt = service.buildPrompt(null);

            assertTrue(prompt.contains("**Topic:** Open exploration"));
        }

        @Test
        void topicIsTrimmed() {
            when(project.getBasePath()).thenReturn("/nonexistent");
            when(project.getService(ExploreContextService.class)).thenReturn(contextService);
            when(contextService.assembleContext()).thenReturn("context");

            String prompt = service.buildPrompt("  auth system  ");

            assertTrue(prompt.contains("**Topic:** auth system"));
            assertFalse(prompt.contains("  auth system  "));
        }
    }

    @Nested
    class DefaultPromptContent {

        @Test
        void defaultPromptCoversKeyElements() {
            String prompt = ExplorePromptService.DEFAULT_EXPLORE_PROMPT;

            assertTrue(prompt.contains("explore mode"));
            assertTrue(prompt.contains("thinking, not implementing"));
            assertTrue(prompt.contains("Curious, not prescriptive"));
            assertTrue(prompt.contains("Guardrails"));
            assertTrue(prompt.contains("Don't implement"));
        }
    }
}
