<#-- Page frame for every login page: top bar with brand and language switch, card, footer. -->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="utf-8">
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light dark">
    <title>${msg("hmBrand")}</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700&display=swap" rel="stylesheet">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <script type="module">
        import { startSessionPolling } from "${url.resourcesPath}/js/authChecker.js";
        startSessionPolling("${url.ssoLoginInOtherTabsUrl?no_esc}");
    </script>
    <#if authenticationSession??>
        <script type="module">
            import { checkAuthSession } from "${url.resourcesPath}/js/authChecker.js";
            checkAuthSession("${authenticationSession.authSessionIdHash}");
        </script>
    </#if>
</head>

<body class="hm-page ${bodyClass}" data-page-id="login-${pageId}">
    <header class="hm-top">
        <div class="hm-brand">
            <span class="hm-brand__name">${msg("hmBrand")}</span>
            <span class="hm-brand__overline">Church Management</span>
        </div>
        <#if realm.internationalizationEnabled && locale.supported?size gt 1>
            <nav class="hm-lang" aria-label="${msg("languages")}">
                <#list locale.supported as l>
                    <a class="hm-lang__item<#if l.languageTag == locale.currentLanguageTag> is-active</#if>"
                       href="${l.url}"<#if l.languageTag == locale.currentLanguageTag> aria-current="true"</#if>
                       lang="${l.languageTag}">${l.label}</a>
                </#list>
            </nav>
        </#if>
    </header>

    <main class="hm-stage">
        <div class="hm-card">
            <#nested "badge">
            <div class="hm-head">
                <h1 class="hm-title" id="kc-page-title"><#nested "header"></h1>
                <#nested "description">
            </div>

            <#-- App-initiated actions do not show warnings about the pending action. -->
            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="hm-alert hm-alert--${message.type}" role="alert">
                    <#if message.type = 'success'><@icon name="check-circle"/><#elseif message.type = 'info'><@icon name="info-circle"/><#else><@icon name="exclamation-triangle"/></#if>
                    <span>${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                    <input type="hidden" name="tryAnotherWay" value="on"/>
                    <button type="submit" class="hm-link">${msg("doTryAnotherWay")}</button>
                </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
                <div class="hm-info-slot"><#nested "info"></div>
            </#if>
        </div>
    </main>

    <footer class="hm-footer">${msg("hmFooter")}</footer>
</body>
</html>
</#macro>

<#-- Row that leads back to the login page. -->
<#macro backRow href>
    <a class="hm-back" href="${href}"><@icon name="arrow-left"/><span>${msg("hmBackToLogin")}</span></a>
</#macro>

<#-- Round status badge above the title: tone is active | pending | rejected, glyph an icon name. -->
<#macro badge tone glyph>
    <div class="hm-badge hm-badge--${tone}" aria-hidden="true"><@icon name=glyph/></div>
</#macro>

<#-- PrimeIcons paths, inline so the theme needs no icon font. -->
<#macro icon name>
    <svg class="hm-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><#switch name>
        <#case "arrow-left"><path d="M11,18.75a.74.74,0,0,1-.53-.22l-6-6a.75.75,0,0,1,0-1.06l6-6a.75.75,0,0,1,1.06,1.06L6.06,12l5.47,5.47a.75.75,0,0,1,0,1.06A.74.74,0,0,1,11,18.75Z"/><path d="M19,12.75H5a.75.75,0,0,1,0-1.5H19a.75.75,0,0,1,0,1.5Z"/><#break>
        <#case "check-circle"><path d="M10.5,15.25A.74.74,0,0,1,10,15L7,12A.75.75,0,0,1,8,11l2.47,2.47L19,5A.75.75,0,0,1,20,6l-9,9A.74.74,0,0,1,10.5,15.25Z"/><path d="M12,21a9,9,0,0,1-7.87-4.66,8.67,8.67,0,0,1-1.07-3.41A9,9,0,0,1,7.66,4.12a8.67,8.67,0,0,1,3.41-1.07,8.86,8.86,0,0,1,3.55.34.75.75,0,1,1-.43,1.43,7.62,7.62,0,0,0-3-.28,7.43,7.43,0,0,0-2.84.89,7.5,7.5,0,0,0-2.2,1.84,7.42,7.42,0,0,0-1.64,5.51,7.43,7.43,0,0,0,.89,2.84,7.5,7.5,0,0,0,1.84,2.2,7.42,7.42,0,0,0,5.51,1.64,7.43,7.43,0,0,0,2.84-.89,7.5,7.5,0,0,0,2.2-1.84,7.42,7.42,0,0,0,1.64-5.51A.75.75,0,1,1,21,11.07a9,9,0,0,1-4.61,8.81A8.67,8.67,0,0,1,12.93,21C12.62,21,12.3,21,12,21Z"/><#break>
        <#case "info-circle"><path d="M12,16.75a.76.76,0,0,1-.75-.75V11a.75.75,0,0,1,1.5,0v5A.76.76,0,0,1,12,16.75Z"/><path d="M12,9.25a.76.76,0,0,1-.75-.75V8a.75.75,0,0,1,1.5,0v.5A.76.76,0,0,1,12,9.25Z"/><path d="M12,21a9,9,0,1,1,9-9A9,9,0,0,1,12,21ZM12,4.5A7.5,7.5,0,1,0,19.5,12,7.5,7.5,0,0,0,12,4.5Z"/><#break>
        <#default><path d="M20,18.75H4a.76.76,0,0,1-.65-.37.77.77,0,0,1,0-.75l8-14a.78.78,0,0,1,1.3,0l8,14a.77.77,0,0,1,0,.75A.76.76,0,0,1,20,18.75ZM5.29,17.25H18.71L12,5.51Z"/><path d="M12,13.25a.76.76,0,0,1-.75-.75V9a.75.75,0,0,1,1.5,0v3.5A.76.76,0,0,1,12,13.25Z"/><path d="M12,16.25a.76.76,0,0,1-.75-.75V15a.75.75,0,0,1,1.5,0v.5A.76.76,0,0,1,12,16.25Z"/>
    </#switch></svg>
</#macro>
