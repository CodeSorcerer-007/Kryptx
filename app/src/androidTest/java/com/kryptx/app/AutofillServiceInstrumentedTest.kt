package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptx.app.feature.autofill.KryptxAutofillService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillServiceInstrumentedTest {

    @Test
    fun testAutofillServiceInstantiation() {
        val service = KryptxAutofillService()
        assertNotNull("KryptxAutofillService should instantiate cleanly", service)
    }

    @Test
    fun testParsedFormModel() {
        val form = KryptxAutofillService.ParsedForm(
            webDomain = "https://accounts.google.com",
            packageName = "com.android.chrome"
        )
        assertNotNull(form.webDomain)
        assertNotNull(form.packageName)
    }
}
