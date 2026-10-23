import { test, expect } from '@playwright/test';
import { Document, Packer, Paragraph, Table, TableCell, TableRow } from 'docx';

test('normal Word conversion never silently falls back when the office runtime is missing', async ({ page }) => {
  const uploads: string[] = [];
  page.on('request', request => { if(request.method()==='POST')uploads.push(request.url()); });
  await page.route('**/office-runtime/manifest.json', route => route.fulfill({status:404,body:'Not packaged'}));
  await page.goto('/tools/word-to-pdf');
  await expect(page.getByRole('checkbox', {name:'Export basic text only (discard document layout)'})).not.toBeChecked();
  const source=await Packer.toBuffer(new Document({sections:[{children:[new Paragraph({text:'Preserve authored layout',heading:'Heading1'}),new Table({rows:[new TableRow({children:[new TableCell({children:[new Paragraph('Required table')]})]})]})]}]}));
  await page.locator('.dropzone input[type=file]').setInputFiles({name:'formatted.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:source});
  await page.getByRole('button',{name:'Convert to PDF',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('missing from this deployment');
  await expect(page.getByText('Your file is ready')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Save',exact:true})).toHaveCount(0);
  expect(uploads).toEqual([]);
  expect(await page.locator('iframe[title="Offline document renderer"]').count()).toBe(0);
});

test('explicit text export is separately named and produces a text-only filename', async ({page}) => {
  await page.goto('/tools/word-to-pdf');
  await page.getByRole('checkbox',{name:'Export basic text only (discard document layout)'}).check();
  const source=await Packer.toBuffer(new Document({sections:[{children:[new Paragraph('Explicit plain text')]}]}));
  await page.locator('.dropzone input[type=file]').setInputFiles({name:'plain.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:source});
  await page.getByRole('button',{name:'Export basic text PDF',exact:true}).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Save',exact:true}).click();
  expect((await pending).suggestedFilename()).toBe('plain-text-only.pdf');
});
