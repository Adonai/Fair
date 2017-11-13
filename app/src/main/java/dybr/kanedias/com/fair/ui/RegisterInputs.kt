package dybr.kanedias.com.fair.ui

import convalida.annotations.ConfirmPasswordValidation
import convalida.annotations.EmailValidation
import convalida.annotations.NotEmptyValidation
import convalida.annotations.PasswordValidation
import dybr.kanedias.com.fair.AddAccountFragment
import dybr.kanedias.com.fair.R

/**
 * Validation helper class for registering.
 * This has to be upper-level class because of way how Convalida annotation processing works in inner classes.
 */
class RegisterInputs(parent: AddAccountFragment) {
    val parent = parent

    @JvmField
    @EmailValidation(R.string.invalid_email)
    val email = parent.emailInput

    @JvmField
    @NotEmptyValidation(R.string.should_not_be_empty)
    val username = parent.usernameInput

    @JvmField
    @PasswordValidation(errorMessage = R.string.invalid_password)
    val password = parent.passwordInput

    @JvmField
    @ConfirmPasswordValidation(R.string.passwords_not_match)
    val confirmPassword = parent.confirmPasswordInput

    @JvmField
    @NotEmptyValidation(R.string.should_not_be_empty)
    val namespace = parent.namespaceInput

    // required for validator to work
    fun getString(id: Int): String {
        return parent.getString(id)
    }
}