export type Point = { x: number; y: number };
export type DocumentCorners = { topLeft: Point; topRight: Point; bottomRight: Point; bottomLeft: Point };
export type ScanFilter = 'color' | 'grayscale' | 'black-white';
export type ScanOptions = { filter: ScanFilter; brightness: number; contrast: number; sharpen: boolean };

export const DEFAULT_CORNERS: DocumentCorners = {
  topLeft: { x: .04, y: .04 }, topRight: { x: .96, y: .04 },
  bottomRight: { x: .96, y: .96 }, bottomLeft: { x: .04, y: .96 }
};

type LoadedImage = ImageBitmap | HTMLImageElement;

const load = async (file: Blob): Promise<LoadedImage> => {
  if ('createImageBitmap' in window) {
    try {
      const bitmap = await createImageBitmap(file);
      if (bitmap.width && bitmap.height) return bitmap;
      bitmap.close();
    } catch { /* Some WebViews reject camera JPEG metadata that an HTML image can decode. */ }
  }
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    if (!image.naturalWidth || !image.naturalHeight) throw new Error('The captured image could not be decoded.');
    return image;
  } catch {
    throw new Error('The captured image could not be decoded.');
  } finally {
    URL.revokeObjectURL(url);
  }
};

const release = (image: LoadedImage) => { if ('close' in image) image.close(); };

export const detectDocumentCorners = async (file: Blob): Promise<DocumentCorners> => {
  const bitmap = await load(file);
  try {
    const scale = Math.min(1, 900 / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(40, Math.round(bitmap.width * scale));
    const height = Math.max(40, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) throw new Error('Image analysis is unavailable in this browser.');
    context.drawImage(bitmap, 0, 0, width, height);
    const pixels = context.getImageData(0, 0, width, height).data;
    const gray = new Float32Array(width * height);
    for (let i = 0; i < gray.length; i++) gray[i] = pixels[i * 4] * .299 + pixels[i * 4 + 1] * .587 + pixels[i * 4 + 2] * .114;
    const magnitude = new Float32Array(width * height);
    let sum = 0; let squared = 0; let samples = 0;
    for (let y = 1; y < height - 1; y++) for (let x = 1; x < width - 1; x++) {
      const i = y * width + x;
      const gx = -gray[i - width - 1] + gray[i - width + 1] - 2 * gray[i - 1] + 2 * gray[i + 1] - gray[i + width - 1] + gray[i + width + 1];
      const gy = -gray[i - width - 1] - 2 * gray[i - width] - gray[i - width + 1] + gray[i + width - 1] + 2 * gray[i + width] + gray[i + width + 1];
      const value = Math.hypot(gx, gy); magnitude[i] = value; sum += value; squared += value * value; samples++;
    }
    const mean = sum / samples; const deviation = Math.sqrt(Math.max(0, squared / samples - mean * mean));
    const threshold = mean + deviation * 1.2;
    const marginX = Math.round(width * .025); const marginY = Math.round(height * .025);
    let tl = { x: width * .06, y: height * .06, score: Infinity };
    let tr = { x: width * .94, y: height * .06, score: -Infinity };
    let br = { x: width * .94, y: height * .94, score: -Infinity };
    let bl = { x: width * .06, y: height * .94, score: Infinity };
    let strong = 0;
    for (let y = marginY; y < height - marginY; y++) for (let x = marginX; x < width - marginX; x++) {
      if (magnitude[y * width + x] < threshold) continue;
      strong++;
      const sumScore = x + y; const diffScore = x - y;
      if (sumScore < tl.score) tl = { x, y, score: sumScore };
      if (diffScore > tr.score) tr = { x, y, score: diffScore };
      if (sumScore > br.score) br = { x, y, score: sumScore };
      if (diffScore < bl.score) bl = { x, y, score: diffScore };
    }
    if (strong < Math.max(100, width * height * .002)) return structuredClone(DEFAULT_CORNERS);
    const normalize = ({ x, y }: Point) => ({ x: Math.min(.99, Math.max(.01, x / width)), y: Math.min(.99, Math.max(.01, y / height)) });
    return { topLeft: normalize(tl), topRight: normalize(tr), bottomRight: normalize(br), bottomLeft: normalize(bl) };
  } finally { release(bitmap); }
};

export const renderScannedPage = async (file: Blob, corners: DocumentCorners, options: ScanOptions): Promise<Blob> => {
  const ordered = [corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft];
  if (ordered.some((point) => !Number.isFinite(point.x) || !Number.isFinite(point.y) || point.x < 0 || point.y < 0 || point.x > 1 || point.y > 1)) throw new Error('Document corners must be within the image.');
  const turns = ordered.map((a, i) => { const b = ordered[(i + 1) % 4]; const c = ordered[(i + 2) % 4]; return (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x); });
  if (turns.some((turn) => turn <= .0001)) throw new Error('The selected corners must form a rectangle-like document without crossing.');
  if (![options.brightness, options.contrast].every((value) => Number.isFinite(value) && value >= -80 && value <= 80)) throw new Error('Brightness and contrast must be between -80 and 80.');
  const bitmap = await load(file);
  try {
    if (bitmap.width * bitmap.height > 24_000_000) throw new Error('Scanner images must be at most 24 megapixels. Resize the image before scanning.');
    const sourcePoints = [corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft].map((point) => ({ x: point.x * bitmap.width, y: point.y * bitmap.height }));
    const rawWidth = Math.max(distance(sourcePoints[0], sourcePoints[1]), distance(sourcePoints[3], sourcePoints[2]));
    const rawHeight = Math.max(distance(sourcePoints[0], sourcePoints[3]), distance(sourcePoints[1], sourcePoints[2]));
    if (rawWidth < 1 || rawHeight < 1) throw new Error('The selected document corners are too close together.');
    const outputScale = Math.min(8, Math.max(1, 800 / Math.max(rawWidth, rawHeight)));
    const limitedScale = Math.min(outputScale, 2600 / rawWidth, 3600 / rawHeight);
    const outWidth = Math.max(2, Math.round(rawWidth * limitedScale)); const outHeight = Math.max(2, Math.round(rawHeight * limitedScale));
    const sourceCanvas = document.createElement('canvas'); sourceCanvas.width = bitmap.width; sourceCanvas.height = bitmap.height;
    const sourceContext = sourceCanvas.getContext('2d', { willReadFrequently: true });
    if (!sourceContext) throw new Error('Perspective correction is unavailable.');
    sourceContext.drawImage(bitmap, 0, 0);
    const source = sourceContext.getImageData(0, 0, bitmap.width, bitmap.height);
    const targetCanvas = document.createElement('canvas'); targetCanvas.width = outWidth; targetCanvas.height = outHeight;
    const targetContext = targetCanvas.getContext('2d'); if (!targetContext) throw new Error('Perspective correction is unavailable.');
    const target = targetContext.createImageData(outWidth, outHeight);
    const destination = [{ x: 0, y: 0 }, { x: outWidth - 1, y: 0 }, { x: outWidth - 1, y: outHeight - 1 }, { x: 0, y: outHeight - 1 }];
    const h = homography(destination, sourcePoints);
    for (let y = 0; y < outHeight; y++) for (let x = 0; x < outWidth; x++) {
      const denominator = h[6] * x + h[7] * y + 1;
      const sx = (h[0] * x + h[1] * y + h[2]) / denominator;
      const sy = (h[3] * x + h[4] * y + h[5]) / denominator;
      sample(source.data, bitmap.width, bitmap.height, sx, sy, target.data, (y * outWidth + x) * 4);
    }
    enhance(target.data, options, outWidth);
    targetContext.putImageData(target, 0, 0);
    return await new Promise<Blob>((resolve, reject) => targetCanvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('The corrected scan could not be encoded.')), 'image/jpeg', .92));
  } finally { release(bitmap); }
};

const distance = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y);

const homography = (from: Point[], to: Point[]) => {
  const matrix: number[][] = [];
  for (let i = 0; i < 4; i++) {
    const { x, y } = from[i]; const target = to[i];
    matrix.push([x, y, 1, 0, 0, 0, -target.x * x, -target.x * y, target.x]);
    matrix.push([0, 0, 0, x, y, 1, -target.y * x, -target.y * y, target.y]);
  }
  for (let column = 0; column < 8; column++) {
    let pivot = column;
    for (let row = column + 1; row < 8; row++) if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
    [matrix[column], matrix[pivot]] = [matrix[pivot], matrix[column]];
    const divisor = matrix[column][column]; if (Math.abs(divisor) < 1e-10) throw new Error('The selected corners do not form a valid document.');
    for (let value = column; value < 9; value++) matrix[column][value] /= divisor;
    for (let row = 0; row < 8; row++) if (row !== column) {
      const factor = matrix[row][column];
      for (let value = column; value < 9; value++) matrix[row][value] -= factor * matrix[column][value];
    }
  }
  return matrix.map((row) => row[8]);
};

const sample = (source: Uint8ClampedArray, width: number, height: number, x: number, y: number, target: Uint8ClampedArray, offset: number) => {
  const sx = Math.max(0, Math.min(width - 1, Math.round(x))); const sy = Math.max(0, Math.min(height - 1, Math.round(y)));
  const sourceOffset = (sy * width + sx) * 4;
  target[offset] = source[sourceOffset]; target[offset + 1] = source[sourceOffset + 1]; target[offset + 2] = source[sourceOffset + 2]; target[offset + 3] = 255;
};

const enhance = (pixels: Uint8ClampedArray, options: ScanOptions, width: number) => {
  const contrast = (259 * (options.contrast + 255)) / (255 * (259 - options.contrast));
  for (let i = 0; i < pixels.length; i += 4) {
    let r = contrast * (pixels[i] - 128) + 128 + options.brightness;
    let g = contrast * (pixels[i + 1] - 128) + 128 + options.brightness;
    let b = contrast * (pixels[i + 2] - 128) + 128 + options.brightness;
    if (options.filter !== 'color') {
      const gray = r * .299 + g * .587 + b * .114;
      r = g = b = options.filter === 'black-white' ? (gray > 155 ? 255 : 0) : gray;
    }
    pixels[i] = r; pixels[i + 1] = g; pixels[i + 2] = b;
  }
  if (options.sharpen) {
    const copy = new Uint8ClampedArray(pixels);
    // A conservative horizontal unsharp pass avoids halos and never crosses a row boundary.
    const height = pixels.length / 4 / width;
    for (let y = 0; y < height; y++) for (let x = 1; x < width - 1; x++) {
      const offset = (y * width + x) * 4;
      for (let channel = 0; channel < 3; channel++) pixels[offset + channel] = Math.max(0, Math.min(255, copy[offset + channel] * 1.5 - copy[offset - 4 + channel] * .25 - copy[offset + 4 + channel] * .25));
    }
  }
};
