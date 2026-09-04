import { mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { chromium } from 'playwright-core'

const frontendUrl = process.env.SAHHA_FRONTEND_URL || 'http://localhost:5173'
const gatewayUrl = process.env.SAHHA_GATEWAY_URL || 'http://localhost:8079'
const output = process.env.SAHHA_ROLE_SMOKE_OUTPUT || resolve(tmpdir(), 'sahha-role-live')
const chromePath = process.env.SAHHA_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

const accounts = [
  {
    role:'doctor',
    email:process.env.SAHHA_DOCTOR_EMAIL,
    password:process.env.SAHHA_DOCTOR_PASSWORD,
    routePrefix:'/doctor/',
    routes:[
      { name:'doctor-appointments-1440', path:'/doctor/appointments', viewport:{ width:1440, height:1000 } },
      { name:'doctor-availability-1024', path:'/doctor/availability', viewport:{ width:1024, height:900 } },
      { name:'doctor-overview-375', path:'/doctor/overview', viewport:{ width:375, height:900 }, notifications:true },
    ],
  },
  {
    role:'receptionist',
    email:process.env.SAHHA_RECEPTIONIST_EMAIL,
    password:process.env.SAHHA_RECEPTIONIST_PASSWORD,
    routePrefix:'/reception/',
    routes:[
      { name:'reception-patients-1440', path:'/reception/patients', viewport:{ width:1440, height:1000 } },
      { name:'reception-appointments-1024', path:'/reception/appointments', viewport:{ width:1024, height:900 } },
      { name:'reception-patients-375', path:'/reception/patients', viewport:{ width:375, height:900 } },
    ],
  },
]

for (const account of accounts) {
  if (!account.email || !account.password) {
    throw new Error(`Missing ${account.role} credentials. Run the repository PowerShell wrapper so passwords are prompted securely.`)
  }
}

await mkdir(output, { recursive:true })

const browser = await chromium.launch({ headless:true, executablePath:chromePath })
const report = {}

async function login(page, account) {
  await page.goto(`${frontendUrl}/login`)
  await page.waitForLoadState('networkidle')
  if (await page.getByText('Demo identity', { exact:true }).count()) {
    throw new Error('The frontend is running with mock authentication. Start it with VITE_USE_AUTH_MOCKS=false.')
  }

  await page.getByLabel('Email', { exact:true }).fill(account.email)
  await page.getByLabel('Password', { exact:true }).fill(account.password)
  await page.getByRole('button', { name:/Enter workspace/i }).click()
  await page.waitForURL(url => !url.pathname.startsWith('/login'), { timeout:20_000 })
  await page.waitForLoadState('networkidle')

  if (new URL(page.url()).pathname === '/organisations/select') {
    const choice = page.locator('.organisation-selector__choice').first()
    await choice.waitFor({ state:'visible', timeout:15_000 })
    await choice.click()
    await page.waitForURL(url => url.pathname !== '/organisations/select', { timeout:20_000 })
    await page.waitForLoadState('networkidle')
  }

  const path = new URL(page.url()).pathname
  if (!path.startsWith(account.routePrefix)) {
    throw new Error(`${account.role} sign-in reached an unexpected route: ${path}`)
  }
}

async function verifySkipLink(page, name) {
  await page.evaluate(() => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  })
  await page.keyboard.press('Tab')
  const className = await page.evaluate(() =>
    document.activeElement instanceof HTMLElement ? document.activeElement.className : '',
  )
  if (!String(className).includes('skip-link')) {
    throw new Error(`${name} does not expose its skip link as the first keyboard target.`)
  }
}

async function verifyNotificationPanel(page, name) {
  const button = page.getByRole('button', { name:'Notifications', exact:true })
  await button.waitFor({ state:'visible' })
  await button.click()
  const panel = page.getByRole('dialog', { name:'Notifications' })
  await panel.waitFor({ state:'visible' })
  const bounds = await panel.boundingBox()
  const viewport = page.viewportSize()
  if (!bounds || !viewport || bounds.x < 0 || bounds.x + bounds.width > viewport.width + 1) {
    throw new Error(`${name} notification panel escapes its viewport: ${JSON.stringify({ bounds, viewport })}`)
  }
  await page.keyboard.press('Escape')
  await panel.waitFor({ state:'detached' })
}

async function inspect(page, account, scenario) {
  await page.setViewportSize(scenario.viewport)
  await page.goto(`${frontendUrl}${scenario.path}`)
  await page.waitForLoadState('networkidle')

  const currentPath = new URL(page.url()).pathname
  if (currentPath !== scenario.path) {
    throw new Error(`${scenario.name} was redirected to ${currentPath}.`)
  }

  const state = await page.evaluate(() => ({
    viewport:window.innerWidth,
    document:document.documentElement.scrollWidth,
    body:document.body.scrollWidth,
    title:document.title,
  }))
  if (state.document > state.viewport + 1 || state.body > state.viewport + 1) {
    throw new Error(`${scenario.name} has document-level horizontal overflow: ${JSON.stringify(state)}`)
  }
  if (!state.title.endsWith('| Sahha')) {
    throw new Error(`${scenario.name} has an inconsistent document title: ${state.title}`)
  }

  const productIdentity = await page.locator('.brand').first().textContent()
  if (!productIdentity?.toUpperCase().includes('SAHHA') || productIdentity.toUpperCase().includes('AEGIS')) {
    throw new Error(`${scenario.name} does not expose the approved Sahha product identity.`)
  }

  const failedApiResponses = await page.evaluate(() => window.__sahhaFailedApiResponses || [])
  if (failedApiResponses.length) {
    throw new Error(`${scenario.name} received failed API responses: ${JSON.stringify(failedApiResponses)}`)
  }

  await verifySkipLink(page, scenario.name)
  if (scenario.notifications) await verifyNotificationPanel(page, scenario.name)
  await page.screenshot({ path:resolve(output, `${scenario.name}.png`), fullPage:true })

  report[scenario.name] = {
    role:account.role,
    path:scenario.path,
    viewport:scenario.viewport,
    documentWidth:state.document,
    bodyWidth:state.body,
    title:state.title,
    sahhaBrand:true,
    apiErrors:0,
  }
}

try {
  for (const account of accounts) {
    const context = await browser.newContext({
      viewport:{ width:1440, height:1000 },
      reducedMotion:'reduce',
    })
    const page = await context.newPage()
    await page.addInitScript(() => {
      window.__sahhaFailedApiResponses = []
    })
    page.on('response', response => {
      if (response.url().startsWith(`${gatewayUrl}/api/`) && response.status() >= 400) {
        void page.evaluate(failure => {
          window.__sahhaFailedApiResponses.push(failure)
        }, { url:response.url(), status:response.status() })
      }
    })

    try {
      await login(page, account)
      for (const scenario of account.routes) await inspect(page, account, scenario)

      await page.setViewportSize({ width:1440, height:1000 })
      await page.goto(`${frontendUrl}${account.routes[0].path}`)
      await page.waitForLoadState('networkidle')
      await page.getByRole('button', { name:/Leave (doctor|demo) portal/i }).click()
      await page.waitForURL(url => url.pathname === '/login', { timeout:15_000 })
    } finally {
      await context.close()
    }
  }

  console.log(JSON.stringify({ frontendUrl, gatewayUrl, output, scenarios:report }, null, 2))
} finally {
  await browser.close()
}
