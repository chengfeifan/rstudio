/*
 * SessionConstants.hpp
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#ifndef SESSION_CONSTANTS_HPP
#define SESSION_CONSTANTS_HPP

#define kEventsPending                    "ep"

#define kRStudioUserIdentity              "RSTUDIO_USER_IDENTITY"
#define kRStudioUserIdentityDisplay       "X-RStudioUserIdentity"
#define kRStudioLimitRpcClientUid         "RSTUDIO_LIMIT_RPC_CLIENT_UID"
#define kRSessionPortNumber               "RSTUDIO_SESSION_PORT"
#define kRSessionStandalonePortNumber     "RSTUDIO_STANDALONE_PORT"
#define kRStudioSessionStream             "RSTUDIO_SESSION_STREAM"
#define kRStudioMultiSession              "RSTUDIO_MULTI_SESSION"
#define kRStudioSessionScopeProject       "RSTUDIO_SESSION_SCOPE_PROJECT"
#define kRStudioSessionScopeId            "RSTUDIO_SESSION_SCOPE_ID"
#define kRStudioSessionRoute              "RSTUDIO_SESSION_ROUTE"
#define kRStudioRequiredUserGroup         "RSTUDIO_REQUIRED_USER_GROUP"
#define kRStudioMinimumUserId             "RSTUDIO_MINIMUM_USER_ID"

#define kRStudioDefaultRVersion           "RSTUDIO_DEFAULT_R_VERSION"
#define kRStudioDefaultRVersionHome       "RSTUDIO_DEFAULT_R_VERSION_HOME"

#define kRStudioUserHomePage              "RSTUDIO_USER_HOME_PAGE"

#define kProgramModeSessionOption         "program-mode"
#define kSessionProgramModeDesktop        "desktop"
#define kSessionProgramModeServer         "server"

#define kShowUserIdentitySessionOption    "show-user-identity"
#define kUserIdentitySessionOption        "user-identity"
#define kUserIdentitySessionOptionShort   "u"

#define kProjectSessionOption             "project"
#define kProjectSessionOptionShort        "p"

#define kScopeSessionOption               "scope"
#define kScopeSessionOptionShort          "s"

#define kVerifyInstallationSessionOption  "verify-installation"

#define kRunTestsSessionOption            "run-tests"

#define kTimeoutSessionOption             "session-timeout-minutes"
#define kDisconnectedTimeoutSessionOption "session-disconnected-timeout-minutes"

// NOTE: literal versions of these are depended upon by the desktop/rsinverse
// project so they should be updated there as well if they are changed
#define kLocalUriLocationPrefix           "/rsession-local/"
#define kPostbackUriScope                 "postback/"
#define kPostbackExitCodeHeader           "X-Postback-ExitCode"

#define kMonitoredPath      "monitored"
#define kListsPath          "lists"
#define kProjectMruList     "project_mru"

#define kUserSettingsDir       "user-settings"
#define kUserSettingsFile      kUserSettingsDir

#define kServerHomeSetting     "showUserHomePage"
#define kServerHomeAlways      "always"
#define kServerHomeNever       "never"
#define kServerHomeSessions    "sessions"

#define kReuseSessionsForProjectLinksSettings "reuseSessionsForProjectLinks"

#define kRStudioNoTransformRedirect "X-RStudio-NoTransformRedirect"

#endif // SESSION_CONSTANTS_HPP

