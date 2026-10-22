import { describe, expect, it } from 'vitest';
import { DEFAULT_CORNERS, renderScannedPage } from './scanner';

describe('scanner geometry validation', () => {
  it('rejects crossed corners before attempting to decode an image', async () => {
    await expect(renderScannedPage(new Blob(), { ...DEFAULT_CORNERS, topRight: DEFAULT_CORNERS.bottomLeft, bottomLeft: DEFAULT_CORNERS.topRight }, { filter: 'color', brightness: 0, contrast: 0, sharpen: false })).rejects.toThrow(/without crossing/);
  });
});
