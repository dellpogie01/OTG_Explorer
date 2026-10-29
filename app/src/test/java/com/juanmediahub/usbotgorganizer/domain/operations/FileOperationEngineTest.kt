package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.OperationOutcome
import com.juanmediahub.usbotgorganizer.domain.model.TransferMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationEngineTest {
    private val keepBoth = ConflictResolver { ConflictPolicy.KEEP_BOTH }

    @Test
    fun `failed move fallback keeps the source when the delete fails`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        val source = fake.addFile("report.txt", "hello".toByteArray(), "text/plain")
        fake.providerMoveFails = true
        fake.failDeleteOf += source.ref.documentId

        val summary = engine.transfer(
            listOf(source),
            destination.ref,
            TransferMode.MOVE,
            keepBoth
        )

        assertEquals(OperationOutcome.PARTIAL, summary.outcome)
        assertEquals(1, summary.completedItems)
        assertEquals(1, summary.sourceDeleteFailures)
        assertEquals(FileOperationEngine.CODE_SOURCE_DELETE_FAILED, summary.message)
        assertTrue(fake.exists(fake.root, "report.txt"))
        assertTrue(fake.exists(destination.ref, "report.txt"))
        assertEquals(
            "hello",
            fake.contentOf(destination.ref, "report.txt")?.toString(Charsets.UTF_8)
        )
    }

    @Test
    fun `verification failure keeps the source and removes the incomplete copy`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        val source = fake.addFile("report.txt", "hello".toByteArray(), "text/plain")
        fake.providerMoveFails = true
        fake.truncateWritesFor += "report.txt"

        val summary = engine.transfer(
            listOf(source),
            destination.ref,
            TransferMode.MOVE,
            keepBoth
        )

        assertEquals(OperationOutcome.FAILURE, summary.outcome)
        assertEquals(0, summary.completedItems)
        assertEquals(1, summary.failedItems)
        assertEquals(FileOperationEngine.CODE_VERIFY_FAILED, summary.message)
        assertTrue(fake.exists(fake.root, "report.txt"))
        assertFalse(fake.exists(destination.ref, "report.txt"))
        assertEquals(0, summary.incompleteCleanupFailures)
    }

    @Test
    fun `cancelling during a copy removes the incomplete destination file`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        val source = fake.addFile("report.txt", "hello".toByteArray(), "text/plain")
        fake.openOutputGate = CompletableDeferred()

        val job = launch {
            engine.transfer(listOf(source), destination.ref, TransferMode.COPY, keepBoth)
        }
        advanceUntilIdle()
        assertTrue(fake.exists(destination.ref, "report.txt"))

        job.cancelAndJoin()
        advanceUntilIdle()

        assertFalse(fake.exists(destination.ref, "report.txt"))
        assertTrue(fake.exists(fake.root, "report.txt"))
        assertEquals(OperationOutcome.CANCELLED, engine.summary.value?.outcome)
        assertEquals(FileOperationEngine.CODE_CANCELLED, engine.summary.value?.message)
    }

    @Test
    fun `copying a folder into itself is refused`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val folder = fake.addFolder("Photos")
        fake.addFile("inside.jpg", ByteArray(3) { 1 }, "image/jpeg", parent = folder.ref)

        val summary = engine.transfer(
            listOf(folder),
            folder.ref,
            TransferMode.COPY,
            keepBoth
        )

        assertEquals(OperationOutcome.FAILURE, summary.outcome)
        assertEquals(1, summary.failedItems)
        assertEquals(FileOperationEngine.CODE_SELF_COPY, summary.message)
        assertEquals(1, fake.countChildren(folder.ref))
    }

    @Test
    fun `copying a folder into its own descendant is refused`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val parent = fake.addFolder("Photos")
        val child = fake.addFolder("2026", parent = parent.ref)

        val summary = engine.transfer(
            listOf(parent),
            child.ref,
            TransferMode.COPY,
            keepBoth
        )

        assertEquals(OperationOutcome.FAILURE, summary.outcome)
        assertEquals(1, summary.failedItems)
        assertEquals(FileOperationEngine.CODE_AMBIGUOUS_FOLDER, summary.message)
        assertEquals(0, fake.countChildren(child.ref))
    }

    @Test
    fun `partial results report completed and failed items separately`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        val ok = fake.addFile("ok.txt", "one".toByteArray(), "text/plain")
        val bad = fake.addFile("bad.txt", "two".toByteArray(), "text/plain")
        fake.failCreateFileFor += "bad.txt"

        val summary = engine.transfer(
            listOf(ok, bad),
            destination.ref,
            TransferMode.COPY,
            keepBoth
        )

        assertEquals(OperationOutcome.PARTIAL, summary.outcome)
        assertEquals(1, summary.completedItems)
        assertEquals(1, summary.failedItems)
        assertEquals(0, summary.skippedItems)
        assertTrue(fake.exists(destination.ref, "ok.txt"))
        assertFalse(fake.exists(destination.ref, "bad.txt"))
    }

    @Test
    fun `keep both never overwrites an existing destination file`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        fake.addFile("report.txt", "old".toByteArray(), "text/plain", parent = destination.ref)
        val source = fake.addFile("report.txt", "new".toByteArray(), "text/plain")

        val summary = engine.transfer(
            listOf(source),
            destination.ref,
            TransferMode.COPY,
            keepBoth
        )

        assertEquals(OperationOutcome.SUCCESS, summary.outcome)
        assertEquals(1, summary.completedItems)
        assertEquals(
            "old",
            fake.contentOf(destination.ref, "report.txt")?.toString(Charsets.UTF_8)
        )
        assertEquals(
            "new",
            fake.contentOf(destination.ref, "report (1).txt")?.toString(Charsets.UTF_8)
        )
    }

    @Test
    fun `skip conflicts do not copy the item`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        fake.addFile("report.txt", "old".toByteArray(), "text/plain", parent = destination.ref)
        val source = fake.addFile("report.txt", "new".toByteArray(), "text/plain")

        val summary = engine.transfer(
            listOf(source),
            destination.ref,
            TransferMode.COPY,
            ConflictResolver { ConflictPolicy.SKIP }
        )

        assertEquals(OperationOutcome.SUCCESS, summary.outcome)
        assertEquals(1, summary.skippedItems)
        assertEquals(1, fake.countChildren(destination.ref))
    }

    @Test
    fun `cancel conflicts leave no destination item behind`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        fake.addFile("report.txt", "old".toByteArray(), "text/plain", parent = destination.ref)
        val source = fake.addFile("report.txt", "new".toByteArray(), "text/plain")

        val summary = engine.transfer(
            listOf(source),
            destination.ref,
            TransferMode.COPY,
            ConflictResolver { ConflictPolicy.CANCEL }
        )

        assertEquals(OperationOutcome.CANCELLED, summary.outcome)
        assertEquals(1, fake.countChildren(destination.ref))
        assertNull(fake.contentOf(destination.ref, "report (1).txt"))
    }

    @Test
    fun `delete reports failures without claiming success`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val kept = fake.addFile("kept.txt", "a".toByteArray(), "text/plain")
        val deleted = fake.addFile("deleted.txt", "b".toByteArray(), "text/plain")
        fake.failDeleteOf += kept.ref.documentId

        val summary = engine.deleteItems(listOf(kept, deleted))

        assertEquals(OperationOutcome.PARTIAL, summary.outcome)
        assertEquals(1, summary.completedItems)
        assertEquals(1, summary.failedItems)
        assertTrue(fake.exists(fake.root, "kept.txt"))
        assertFalse(fake.exists(fake.root, "deleted.txt"))
    }

    @Test
    fun `copying a folder copies its children`() = runTest {
        val fake = FakeStorageAccess()
        val engine = engine(fake)
        val destination = fake.addFolder("Destination")
        val folder = fake.addFolder("Photos")
        fake.addFile("a.jpg", "a".toByteArray(), "image/jpeg", parent = folder.ref)
        fake.addFile("b.jpg", "bb".toByteArray(), "image/jpeg", parent = folder.ref)

        val summary = engine.transfer(
            listOf(folder),
            destination.ref,
            TransferMode.COPY,
            keepBoth
        )

        assertEquals(OperationOutcome.SUCCESS, summary.outcome)
        assertEquals(1, summary.completedItems)
        val copiedFolder = fake.child(destination.ref, "Photos")
        assertNotNull(copiedFolder)
        assertEquals(
            "a",
            fake.contentOf(copiedFolder!!.ref, "a.jpg")?.toString(Charsets.UTF_8)
        )
        assertEquals(
            "bb",
            fake.contentOf(copiedFolder.ref, "b.jpg")?.toString(Charsets.UTF_8)
        )
    }

    private fun TestScope.engine(fake: FakeStorageAccess): FileOperationEngine {
        return FileOperationEngine(fake, StandardTestDispatcher(testScheduler))
    }
}
