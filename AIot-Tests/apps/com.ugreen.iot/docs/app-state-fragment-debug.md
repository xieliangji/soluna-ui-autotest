# App-State Fragment Debug Record

This note records the intended debug paths for `AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml`.

Each case starts from app restart. After restart and a fixed 5s startup wait, only two root pages are expected:

- login page
- device page

If the root page is the device page, login state is determined only through the real app flow: open Mine, tap the avatar/profile area, then branch by the result.

- Guest state opens the go-login prompt.
- Logged-in state enters the personal information page.

`restartApp` is intentionally atomic: restart, then wait 5s. It does not detect pages, handle popups, or infer login state.

Assertions inside these fragments are limited to:

- `if` predicates used as branch decisions.
- the final target-state assertion at the end of each target fragment.

Do not add path assertions between normal actions.

## Debug Command

Use the installed CLI for interactive debugging. Gradle `JavaExec` may close stdin and exit the shell immediately.

```bash
build/install/soluna/bin/soluna debug AIot-Tests/apps/com.ugreen.iot/plans/app-state/ios.yaml shell
build/install/soluna/bin/soluna debug AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml shell
```

Start each scenario from a real app restart and capture fresh source after every action:

```text
restart-app
source --out build/soluna-debug/<step-name>.xml
screenshot --out build/soluna-debug/<step-name>.png
```

Do not reuse an old XML file to locate later elements.

## Current Paths

### Login Page

Fragment: `appState.loginPage`

1. Restart app.
2. Wait 5s.
3. If device page is detected, tap Mine, then tap the avatar/profile area.
4. If the go-login prompt appears, tap the positive button and stop on the login page.
5. Otherwise the app is logged in; tap logout, confirm logout, and stop on the login page.
6. Final assertion: `common.loginAgreementCheckbox` exists.

### Guest Device Page

Fragment: `appState.guestDevicePage`

1. Restart app.
2. Wait 5s.
3. If device page is detected, tap Mine, then tap the avatar/profile area.
4. If the go-login prompt appears, the app is already in guest state. Stop there and do not tap the go-login button.
5. Otherwise the app is logged in; tap logout and confirm logout to enter the login page.
6. On the login page, tap `common.loginAgreementCheckbox` only when `common.loginAgreementUncheckedCheckbox` exists.
7. Tap `common.guestEntryButton`.
8. Final assertion: `common.deviceAddButton` exists, or the guest go-login prompt still exists.

The login agreement checkbox and the `同意并继续` prompt are mutually exclusive paths. These fragments use the checkbox path and do not handle the prompt.

### Logged-In Device Page

Fragment: `appState.loggedInDevicePage`

1. Restart app.
2. Wait 5s.
3. If device page is detected, tap Mine, then tap the avatar/profile area.
4. If the go-login prompt appears, tap the positive button, enter credentials, tap `common.loginAgreementCheckbox` only when `common.loginAgreementUncheckedCheckbox` exists, and submit login.
5. Otherwise the app is already logged in; tap Back from personal information and return to the device tab.
6. If restart was already on the login page, enter credentials, tap `common.loginAgreementCheckbox` only when `common.loginAgreementUncheckedCheckbox` exists, and submit login.
7. Final assertion: `common.deviceAddButton` exists.

## Locator Notes

- Use fresh source after each debug action.
- Fragment page-state branches use `assertElementExists`; do not use page source regex as a state predicate.
- Device page detection is only `common.deviceAddButton`, the top-right add-device button.
- Do not use broad `following::` for iOS login submit. The submit button is anchored from the checkbox container with `following-sibling::`.
- Do not use `UgreenAudio` as the login-page state predicate. iOS source can retain hidden or background login-page nodes while the device page is active.
- Prefer Android resource ids.
- `data/app-state.yaml` carries only account data. Do not add page-state regex patterns there.
- If the go-login prompt appears while ensuring guest-device state, do not tap it; the prompt itself proves the current state is guest.
