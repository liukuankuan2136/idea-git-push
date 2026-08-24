package com.ctjsoft.devops.settings

import com.ctjsoft.devops.api.DevOpsCredentials
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class DevOpsCredentialStore {
    private val attributes = CredentialAttributes(generateServiceName("Issue Link Push", "Company DevOps"))

    fun load(): DevOpsCredentials? {
        val credentials = PasswordSafe.instance.get(attributes) ?: return null
        val username = credentials.userName.orEmpty()
        val password = credentials.getPasswordAsString().orEmpty()
        return if (username.isBlank() || password.isBlank()) null else DevOpsCredentials(username, password)
    }

    fun save(username: String, encryptedPassword: String) {
        PasswordSafe.instance.set(attributes, Credentials(username.trim(), encryptedPassword))
    }

    fun clear() {
        PasswordSafe.instance.set(attributes, null)
    }
}

