package com.dark.tool_neuron

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.NoCredentialException
import com.dark.tool_neuron.data.firebase.SignInError
import com.dark.tool_neuron.data.firebase.SignInErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInErrorMapperTest {
    @Test
    fun no_credential_exception_maps_to_NoCredential() {
        val result = SignInErrorMapper.fromGetCredential(NoCredentialException())
        assertEquals(SignInError.NoCredential, result)
    }

    @Test
    fun cancellation_maps_to_Cancelled() {
        val result = SignInErrorMapper.fromGetCredential(GetCredentialCancellationException())
        assertSame(SignInError.Cancelled, result)
    }

    @Test
    fun other_get_credential_exception_maps_to_Other_keeping_cause() {
        val cause = GetCredentialCustomException("test")
        val result = SignInErrorMapper.fromGetCredential(cause)
        assertTrue(result is SignInError.Other)
        assertSame(cause, (result as SignInError.Other).cause)
    }

    @Test
    fun arbitrary_throwable_maps_to_Other() {
        val cause = RuntimeException("network down")
        val result = SignInErrorMapper.fromGetCredential(cause)
        assertTrue(result is SignInError.Other)
        assertSame(cause, (result as SignInError.Other).cause)
    }
}