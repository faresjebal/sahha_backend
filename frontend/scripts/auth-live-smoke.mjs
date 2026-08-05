import { chromium } from 'playwright-core'

const frontendUrl = process.env.SAHHA_FRONTEND_URL || 'http://localhost:5173'
const gatewayUrl = process.env.SAHHA_GATEWAY_URL || 'http://localhost:8079'

const browser = await chromium.launch({
  headless:true,
  executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
})

try {
  const context = await browser.newContext({ viewport:{ width:375, height:900 } })
  const page = await context.newPage()

  await page.goto(`${frontendUrl}/login`)
  await page.waitForLoadState('networkidle')
  if (await page.getByText('Demo identity', { exact:true }).count()) {
    throw new Error('The real Auth mode is not active.')
  }

  await page.getByLabel('Email').fill('missing.frontend.smoke@example.invalid')
  await page.getByLabel('Password').fill('Synthetic passphrase 2026!')
  const loginResponsePromise = page.waitForResponse(response =>
    response.url() === `${gatewayUrl}/api/v1/auth/login`,
  )
  await page.getByRole('button', { name:/Enter workspace/i }).click()
  const loginResponse = await loginResponsePromise
  if (loginResponse.status() !== 401) {
    throw new Error(`Expected safe unknown-user 401, received ${loginResponse.status()}.`)
  }
  await page.getByRole('alert').waitFor({ state:'visible' })

  const cookies = await context.cookies(gatewayUrl)
  const csrfCookie = cookies.find(cookie => cookie.name === 'XSRF-TOKEN')
  if (!csrfCookie?.value) throw new Error('Gateway/Auth did not issue the CSRF cookie.')
  if (cookies.some(cookie => cookie.name === 'SAHHA_ACCESS_TOKEN')) {
    throw new Error('Unknown login unexpectedly issued an access cookie.')
  }

  await page.goto(`${frontendUrl}/register`)
  await page.waitForLoadState('networkidle')
  for (const label of ['First name', 'Last name', 'Email', 'Password', 'Confirm password']) {
    if (!await page.getByLabel(label, { exact:true }).count()) {
      throw new Error(`Registration field is missing: ${label}`)
    }
  }
  const dimensions = await page.evaluate(() => ({
    viewport:window.innerWidth,
    document:document.documentElement.scrollWidth,
    body:document.body.scrollWidth,
  }))
  if (dimensions.document > dimensions.viewport || dimensions.body > dimensions.viewport) {
    throw new Error(`Registration page overflows horizontally: ${JSON.stringify(dimensions)}`)
  }

  console.log(JSON.stringify({
    frontendUrl,
    gatewayUrl,
    realAuthMode:true,
    csrfCookieIssued:true,
    unknownLoginStatus:loginResponse.status(),
    requestIdPresent:Boolean(loginResponse.headers()['x-request-id']),
    accessCookieIssued:false,
    registrationFieldsPresent:true,
    registrationViewport:dimensions,
  }, null, 2))
} finally {
  await browser.close()
}
