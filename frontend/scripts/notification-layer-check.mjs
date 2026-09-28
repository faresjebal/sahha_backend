// Real Chromium/CSS interaction regression; synthetic markup, no backend acceptance claim.
import assert from 'node:assert/strict'
import {after, before, test} from 'node:test'
import {fileURLToPath} from 'node:url'
import {chromium} from 'playwright-core'

let browser
before(async()=>{
  browser=await chromium.launch({headless:true,
    executablePath:process.env.SAHHA_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'})
})
after(async()=>{await browser?.close()})

for(const shell of ['soft-shell','workspace-v3'])for(const width of [1440,375]) {
  test(shell+' notification controls receive pointer events at '+width+'px',async()=>{
    const page=await browser.newPage({viewport:{width,height:960},reducedMotion:'reduce'})
    try {
      // Keep remote font imports offline without rejecting the parent stylesheet.
      await page.route('**/*',route=>route.fulfill({status:200,contentType:'text/css',body:''}))
      await page.setContent('<div class="app-shell '+shell+'"><div class="workspace">'
        +'<header class="topbar"><div class="topbar__context">Synthetic portal</div>'
        +'<div class="topbar__actions"><div class="doctor-notification-center">'
        +'<button class="icon-button notification-trigger" aria-label="Notifications">Bell</button>'
        +'<section hidden class="doctor-notification-panel"><header><h2>Notifications</h2></header>'
        +'<div class="notification-panel-actions"><button>Mark all read</button></div>'
        +'<div class="notification-list"><button>Appointment request declined</button></div>'
        +'</section></div></div></header>'
        +'<main><div class="page" style="min-height:1200px">Synthetic appointment content</div></main>'
        +'</div></div>')
      for(const name of ['styles.css','styles/quality-pass.css','styles/workflows.css',
        'styles/clinical.css','styles/communication.css','styles/live-workspace.css'])
        await page.addStyleTag({path:fileURLToPath(new URL('../src/'+name,import.meta.url))})
      await page.evaluate(()=>{
        document.querySelector('.notification-trigger').addEventListener('click',()=>{
          document.querySelector('.doctor-notification-panel').hidden=false
        })
        document.querySelector('.notification-list button').addEventListener('click',()=>{
          document.body.dataset.acknowledged='true'
        })
      })
      await page.getByRole('button',{name:'Notifications',exact:true}).click()
      const alert=page.getByRole('button',{name:'Appointment request declined',exact:true})
      assert.equal(await alert.evaluate(element=>{
        const box=element.getBoundingClientRect()
        return element.contains(document.elementFromPoint(box.x+box.width/2,box.y+box.height/2))
      }),true,'page content must not intercept the alert')
      await alert.click({timeout:3000})
      assert.equal(await page.evaluate(()=>document.body.dataset.acknowledged),'true')
      await page.getByRole('button',{name:'Mark all read',exact:true}).click({timeout:3000})
    } finally {await page.close()}
  })
}
