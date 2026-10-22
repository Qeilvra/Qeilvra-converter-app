// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { addHistory, formatBytes, readHistory, readSettings, safeBaseName, sniffFileType, validateFile } from './files';

beforeEach(() => localStorage.clear());

describe('file utilities', () => {
  it('formats measured byte counts', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(50 * 1024)).toBe('50.0 KB');
    expect(formatBytes(2.5 * 1024 * 1024)).toBe('2.5 MB');
  });

  it('creates safe, non-empty output base names', () => {
    expect(safeBaseName('My admission photo.png')).toBe('My-admission-photo');
    expect(safeBaseName('💾.pdf')).toBe('fileflow-output');
  });

  it('uses file signatures instead of trusting names and MIME labels', async () => {
    const disguised = new File([new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d])], 'photo.jpg', { type: 'image/jpeg' });
    expect(await sniffFileType(disguised)).toBe('pdf');
    await expect(validateFile(disguised, ['jpeg'])).rejects.toThrow(/contents/i);
  });

  it('rejects empty and oversized files', async () => {
    await expect(validateFile(new File([], 'empty.pdf'), ['pdf'])).rejects.toThrow(/empty/i);
    const large = new File([new Uint8Array(2048)], 'large.pdf', { type: 'application/pdf' });
    await expect(validateFile(large, ['pdf'], .001)).rejects.toThrow(/larger/i);
  });

  it('does not trust malformed persisted settings or history', () => {
    localStorage.setItem('fileflow.settings.v1', JSON.stringify({ theme: 'script', language: 'xx', retainOutputs: 'yes' }));
    localStorage.setItem('fileflow.history.v1', JSON.stringify([{ id: 1, outputSize: 'large' }]));
    expect(readSettings()).toEqual({ theme: 'light', language: 'en', retainOutputs: false });
    expect(readHistory()).toEqual([]);
  });
  it('does not fail successful file generation when local history storage is full', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Quota exceeded', 'QuotaExceededError'); });
    expect(() => addHistory({ operation: 'Test', sourceName: 'source.pdf', outputName: 'result.pdf', outputSize: 10 })).not.toThrow();
    setItem.mockRestore();
  });
});
