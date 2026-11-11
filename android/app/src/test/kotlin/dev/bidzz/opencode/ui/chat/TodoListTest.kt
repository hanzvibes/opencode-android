package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoListTest {

    @Test
    fun `parses all four statuses`() {
        val input = """{"todos":[
            |{"content":"Fix the scroll jump","status":"in_progress"},
            |{"content":"Write the test","status":"pending"},
            |{"content":"Merge the PR","status":"completed"},
            |{"content":"Old idea","status":"cancelled"}
            |]}""".trimMargin()
        val items = parseTodoList(input)
        assertEquals(
            listOf(
                TodoItem("Fix the scroll jump", TodoStatus.IN_PROGRESS),
                TodoItem("Write the test", TodoStatus.PENDING),
                TodoItem("Merge the PR", TodoStatus.COMPLETED),
                TodoItem("Old idea", TodoStatus.CANCELLED),
            ),
            items,
        )
    }

    @Test
    fun `unknown status reads as pending rather than dropping the row`() {
        val items = parseTodoList("""{"todos":[{"content":"Mystery","status":"deferred"}]}""")
        assertEquals(listOf(TodoItem("Mystery", TodoStatus.PENDING)), items)
    }

    @Test
    fun `empty todos array returns an empty list, not null`() {
        assertEquals(emptyList<TodoItem>(), parseTodoList("""{"todos":[]}"""))
    }

    @Test
    fun `malformed entries are skipped, usable ones kept`() {
        val input = """{"todos":[
            |{"content":"Good","status":"pending"},
            |{"status":"pending"},
            |{"content":"","status":"pending"},
            |"not an object",
            |{"content":"Also good","status":"completed"}
            |]}""".trimMargin()
        assertEquals(
            listOf(
                TodoItem("Good", TodoStatus.PENDING),
                TodoItem("Also good", TodoStatus.COMPLETED),
            ),
            parseTodoList(input),
        )
    }

    @Test
    fun `non-JSON input returns null`() {
        assertNull(parseTodoList("not json at all"))
        assertNull(parseTodoList(""))
        assertNull(parseTodoList(null))
    }

    @Test
    fun `missing or non-array todos returns null`() {
        assertNull(parseTodoList("""{"content":"no todos key"}"""))
        assertNull(parseTodoList("""{"todos":"not an array"}"""))
        assertNull(parseTodoList("""[1, 2, 3]"""))
    }
}
