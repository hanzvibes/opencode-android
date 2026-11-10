package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitBranchDetectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `null or empty workspace returns null`() {
        assertNull(detectGitBranch(null))
        assertNull(detectGitBranch(""))
        assertNull(detectGitBranch("   "))
    }

    @Test
    fun `non-existent directory returns null`() {
        assertNull(detectGitBranch("/path/that/does/not/exist/999"))
    }

    @Test
    fun `folder without dot-git returns null`() {
        val project = tempFolder.newFolder("regular_project")
        assertNull(detectGitBranch(project.absolutePath))
    }

    @Test
    fun `git repo on branch main parses branch name`() {
        val project = tempFolder.newFolder("repo_main")
        val gitDir = File(project, ".git").apply { mkdirs() }
        File(gitDir, "HEAD").writeText("ref: refs/heads/main\n")

        assertEquals("main", detectGitBranch(project.absolutePath))
    }

    @Test
    fun `git repo on feature branch parses nested branch name`() {
        val project = tempFolder.newFolder("repo_feature")
        val gitDir = File(project, ".git").apply { mkdirs() }
        File(gitDir, "HEAD").writeText("ref: refs/heads/feature/ui-landing\n")

        assertEquals("feature/ui-landing", detectGitBranch(project.absolutePath))
    }

    @Test
    fun `detached HEAD parses short commit hash`() {
        val project = tempFolder.newFolder("repo_detached")
        val gitDir = File(project, ".git").apply { mkdirs() }
        File(gitDir, "HEAD").writeText("9a8b7c6d5e4f3a2b1c0d\n")

        assertEquals("9a8b7c6", detectGitBranch(project.absolutePath))
    }

    @Test
    fun `worktree dot-git file pointing to real gitdir resolves branch`() {
        val mainRepo = tempFolder.newFolder("main_repo")
        val realGitDir = File(mainRepo, ".git/worktrees/wt1").apply { mkdirs() }
        File(realGitDir, "HEAD").writeText("ref: refs/heads/worktree-branch\n")

        val worktreeDir = tempFolder.newFolder("worktree_dir")
        File(worktreeDir, ".git").writeText("gitdir: ${realGitDir.absolutePath}\n")

        assertEquals("worktree-branch", detectGitBranch(worktreeDir.absolutePath))
    }
}
