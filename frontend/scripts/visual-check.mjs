import { mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { chromium } from 'playwright-core'
import { build, preview } from 'vite'

const baseURL = process.env.AEGIS_PREVIEW_URL || 'http://127.0.0.1:4174'
const output = process.env.AEGIS_VISUAL_OUTPUT || resolve(tmpdir(), 'aegis-visual')
await mkdir(output, { recursive:true })

// The complete role gallery remains a mock-domain regression suite even when
// local development uses the real Auth Service.
process.env.VITE_USE_MOCKS = 'true'
process.env.VITE_USE_AUTH_MOCKS = 'true'
await build({ logLevel:'warn' })

let previewServer
try {
  const response = await fetch(baseURL)
  if (!response.ok) throw new Error('Preview is unavailable')
} catch {
  previewServer = await preview({ preview:{ host:'127.0.0.1', port:4174 } })
}

const browser = await chromium.launch({
  headless:true,
  executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
})

async function signIn(page, role, option) {
  await page.goto(`${baseURL}/login`)
  await page.getByRole('button', { name:new RegExp(role, 'i') }).first().click()
  if (option) await page.getByRole('button', { name:new RegExp(option, 'i') }).click()
  await page.locator('.login-submit').click()
  await page.waitForURL(url => !url.pathname.startsWith('/login'))
  await page.waitForLoadState('networkidle')
}

async function inspect(page, name, path, viewport) {
  await page.setViewportSize(viewport)
  await page.goto(`${baseURL}${path}`)
  await page.waitForLoadState('networkidle')
  const dimensions = await page.evaluate(() => ({
    viewport:window.innerWidth,
    document:document.documentElement.scrollWidth,
    body:document.body.scrollWidth,
  }))
  if (dimensions.document > dimensions.viewport + 1 || dimensions.body > dimensions.viewport + 1) {
    throw new Error(`${name} has document-level horizontal overflow: ${JSON.stringify(dimensions)}`)
  }
  await page.screenshot({ path:resolve(output, `${name}.png`), fullPage:true })
  return dimensions
}

async function verifyFirstTab(page, name) {
  await page.evaluate(() => { if (document.activeElement instanceof HTMLElement) document.activeElement.blur() })
  await page.keyboard.press('Tab')
  const focused = await page.evaluate(() => {
    const active = document.activeElement
    return { tag:active?.tagName, className:active instanceof HTMLElement ? active.className : '' }
  })
  if (!String(focused.className).includes('skip-link')) throw new Error(`${name} does not expose its skip link as the first keyboard target.`)
}

async function verifyDrawer(page, buttonName) {
  await page.getByRole('button', { name:new RegExp(buttonName, 'i') }).first().click()
  await page.getByRole('dialog').waitFor({ state:'visible' })
  await page.keyboard.press('Escape')
  await page.getByRole('dialog').waitFor({ state:'detached' })
}

const report = []
try {
  {
    const context = await browser.newContext({ viewport:{ width:375, height:900 }, reducedMotion:'reduce' })
    const page = await context.newPage()
    report.push(['registration-375', await inspect(page, 'registration-375', '/register', { width:375, height:900 })])
    await signIn(page, 'Patient')
    report.push(['patient-375', await inspect(page, 'patient-375', '/patient/overview', { width:375, height:900 })])
    await verifyFirstTab(page, 'Patient portal')
    report.push(['patient-768', await inspect(page, 'patient-768', '/patient/appointments', { width:768, height:900 })])
    report.push(['patient-messages-375', await inspect(page, 'patient-messages-375', '/patient/messages', { width:375, height:900 })])
    await context.close()
  }
  {
    const context = await browser.newContext({ viewport:{ width:1440, height:1000 }, reducedMotion:'reduce' })
    const page = await context.newPage()
    await signIn(page, 'Hospital super admin')
    report.push(['hospital-1440', await inspect(page, 'hospital-1440', '/hospital/admin/capacity', { width:1440, height:1000 })])
    await verifyFirstTab(page, 'Hospital portal')
    await verifyDrawer(page, 'C-201')
    report.push(['finance-1440', await inspect(page, 'finance-1440', '/hospital/admin/finance', { width:1440, height:1000 })])
    await verifyDrawer(page, 'New invoice')
    await context.close()
  }
  {
    const context = await browser.newContext({ viewport:{ width:1440, height:1000 }, reducedMotion:'reduce' })
    const page = await context.newPage()
    await signIn(page, 'Independent doctor', 'Hospital doctor')
    report.push(['doctor-1440', await inspect(page, 'doctor-1440', '/doctor/clinical', { width:1440, height:1000 })])
    await verifyDrawer(page, 'Document encounter')
    report.push(['doctor-team-1024', await inspect(page, 'doctor-team-1024', '/doctor/team', { width:1024, height:900 })])
    report.push(['doctor-messages-1024', await inspect(page, 'doctor-messages-1024', '/doctor/messages', { width:1024, height:900 })])
    await verifyDrawer(page, 'New conversation')
    await context.close()
  }
  {
    const context = await browser.newContext({ viewport:{ width:1024, height:900 }, reducedMotion:'reduce' })
    const page = await context.newPage()
    await signIn(page, 'Hospital staff', 'Laboratory')
    report.push(['staff-1024', await inspect(page, 'staff-1024', '/staff/work', { width:1024, height:900 })])
    await verifyDrawer(page, 'Process')
    await context.close()
  }
  {
    const context = await browser.newContext({ viewport:{ width:1024, height:900 }, reducedMotion:'reduce' })
    const page = await context.newPage()
    await signIn(page, 'Platform administrator')
    report.push(['platform-1024', await inspect(page, 'platform-1024', '/platform/reviews', { width:1024, height:900 })])
    await context.close()
  }
  console.log(JSON.stringify(Object.fromEntries(report), null, 2))
} finally {
  await browser.close()
  await previewServer?.close()
}
