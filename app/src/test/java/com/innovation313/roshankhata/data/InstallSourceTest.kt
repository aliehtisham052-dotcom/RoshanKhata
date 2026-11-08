package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallSourceTest {
    @Test fun theKhataPagesFlagIsRecognised() {
        assertTrue(InstallSource.isKhataReferrer("khata=1"))
        assertTrue(InstallSource.isKhataReferrer("utm_source=google-play&khata=1"))
    }

    @Test fun anythingElseIsNot() {
        assertFalse(InstallSource.isKhataReferrer(null))
        assertFalse(InstallSource.isKhataReferrer(""))
        assertFalse(InstallSource.isKhataReferrer("utm_source=google-play&utm_medium=organic"))
        assertFalse(InstallSource.isKhataReferrer("khata=10"))
    }
}
