<#import "template.ftl" as layout>
<#-- The error sits in the alert above the fields (Figma Login × Error), not under the input. -->
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "description">
        <p class="hm-desc">${msg("hmLoginDescription")}</p>
    <#elseif section = "form">
        <#if realm.password>
            <#assign hasError = messagesPerField.existsError('username','password')>
            <form id="kc-form-login" class="hm-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                <div class="hm-fields">
                    <#if !usernameHidden??>
                        <div class="hm-field">
                            <label for="username" class="hm-label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                            <input id="username" class="hm-input" name="username" value="${(login.username!'')}" type="text"
                                   autofocus autocomplete="username" dir="ltr"<#if hasError> aria-invalid="true"</#if>/>
                        </div>
                    </#if>
                    <div class="hm-field">
                        <label for="password" class="hm-label">${msg("password")}</label>
                        <input id="password" class="hm-input" name="password" type="password" autocomplete="current-password"
                               dir="ltr"<#if hasError> aria-invalid="true"</#if>/>
                    </div>
                </div>
                <#if realm.rememberMe && !usernameHidden??>
                    <label class="hm-check">
                        <input id="rememberMe" name="rememberMe" type="checkbox"<#if login.rememberMe??> checked</#if>> ${msg("rememberMe")}
                    </label>
                </#if>
                <#if realm.resetPasswordAllowed>
                    <a class="hm-forgot" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                </#if>
                <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                <button class="hm-button" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
            </form>
        </#if>
    </#if>
</@layout.registrationLayout>
