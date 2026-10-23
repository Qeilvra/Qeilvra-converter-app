// Read-only vendor price pages. No documents, account creation, or API keys.
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const output = resolve('.tools/professional-conversion/research'); await mkdir(output, { recursive: true });
const browser = await chromium.launch();
try {
  for (const [name, url] of [['cloudconvert', 'https://cloudconvert.com/pricing'], ['convertapi', 'https://www.convertapi.com/pricing']]) {
    const page = await browser.newPage();
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
      await page.waitForTimeout(8_000);
      const body = await page.locator('body').innerText();
      await writeFile(resolve(output, name + '.txt'), body);
      await page.screenshot({ path: resolve(output, name + '.png'), fullPage: true });
      console.log(name, body.slice(0, 6500));
    } catch (e) { console.log(name, e.message); }
    await page.close();
  }
} finally { await browser.close(); }
