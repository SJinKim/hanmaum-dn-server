<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("emailForgotTitle")}
    <#elseif section = "description">
        <p class="hm-desc">${msg("emailInstruction")}</p>
    <#elseif section = "form">
        <form id="kc-reset-password-form" class="hm-form" action="${url.loginAction}" method="post">
            <div class="hm-field">
                <label for="username" class="hm-label">${msg("email")}</label>
                <input type="text" id="username" name="username" class="hm-input" autofocus dir="ltr"
                       autocomplete="email" placeholder="${msg("hmEmailPlaceholder")}" value="${(auth.attemptedUsername!'')}"
                       <#if messagesPerField.existsError('username')>aria-invalid="true"</#if>/>
            </div>
            <button class="hm-button" type="submit">${msg("hmSendResetLink")}</button>
        </form>
        <@layout.backRow href=url.loginUrl/>
    </#if>
</@layout.registrationLayout>
