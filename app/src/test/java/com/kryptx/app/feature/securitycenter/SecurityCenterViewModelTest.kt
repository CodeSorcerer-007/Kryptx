package com.kryptx.app.feature.securitycenter

import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityCenterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var viewModel: SecurityCenterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVaultRepository = FakeVaultRepository()
        viewModel = SecurityCenterViewModel(fakeVaultRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRunAuditComputesReport() = runTest(testDispatcher) {
        testScheduler.runCurrent()
        assertNotNull(viewModel.auditReport.value)
        assertEquals(95, viewModel.auditReport.value?.overallScore)
        assertFalse(viewModel.isLoading.value)

        viewModel.runAudit()
        testScheduler.runCurrent()
        assertEquals(95, viewModel.auditReport.value?.overallScore)
    }
}
