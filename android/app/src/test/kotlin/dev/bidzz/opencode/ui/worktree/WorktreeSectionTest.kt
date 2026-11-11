package dev.bidzz.opencode.ui.worktree

import dev.bidzz.opencode.core.api.dto.ProjectInfo
import dev.bidzz.opencode.core.api.dto.WorktreeDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class WorktreeSectionTest {

    private fun project(id: String, canonical: String, name: String? = null) =
        ProjectInfo(id = id, canonical = canonical, name = name)

    @Test
    fun `groups worktrees under their projects, sorted`() {
        val projects = listOf(
            project("b", "/home/user/beta", "Beta"),
            project("a", "/home/user/alpha", "Alpha"),
        )
        val byProject = mapOf(
            "b" to listOf(
                WorktreeDirectory("/home/user/beta-wt2"),
                WorktreeDirectory("/home/user/beta-wt1", "git"),
            ),
            "a" to listOf(WorktreeDirectory("/home/user/alpha-wt")),
        )
        val sections = buildWorktreeSections(projects, byProject)
        assertEquals(
            listOf(
                WorktreeSection(
                    projectName = "Alpha",
                    projectPath = "/home/user/alpha",
                    worktrees = listOf(WorktreeDirectory("/home/user/alpha-wt")),
                ),
                WorktreeSection(
                    projectName = "Beta",
                    projectPath = "/home/user/beta",
                    worktrees = listOf(
                        WorktreeDirectory("/home/user/beta-wt1", "git"),
                        WorktreeDirectory("/home/user/beta-wt2"),
                    ),
                ),
            ),
            sections,
        )
    }

    @Test
    fun `projects without worktrees are left out`() {
        val projects = listOf(
            project("a", "/home/user/alpha", "Alpha"),
            project("b", "/home/user/beta", "Beta"),
        )
        val byProject = mapOf("a" to listOf(WorktreeDirectory("/home/user/alpha-wt")))
        val sections = buildWorktreeSections(projects, byProject)
        assertEquals(listOf("Alpha"), sections.map { it.projectName })
    }

    @Test
    fun `project name falls back to its canonical path`() {
        val projects = listOf(project("a", "/home/user/alpha"))
        val byProject = mapOf("a" to listOf(WorktreeDirectory("/home/user/alpha-wt")))
        val sections = buildWorktreeSections(projects, byProject)
        assertEquals("/home/user/alpha", sections.single().projectName)
    }
}
