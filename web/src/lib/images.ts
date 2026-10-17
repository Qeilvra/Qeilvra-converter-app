import { jsPDF } from 'jspdf';
import type { ImageOutputFormat } from '../types';

export type DecodedImage = { image: HTMLImageElement; width: number; height: number; url: string };

export const decodeImage = async (file: File): Promise<DecodedImage> => {
  const url = URL.createObjectURL(file);
  const image = new Image();
  image.decoding = 'async';
  image.src = url;
  try {
    await image.decode();
    return { image, width: image.naturalWidth, height: image.naturalHeight, url };
  } catch {
    URL.revokeObjectURL(url);
    throw new Error('This image could not be decoded. It may be damaged or unsupported.');
  }
};

const canvasToBlob = (canvas: HTMLCanvasElement, type: ImageOutputFormat, quality?: number): Promise<Blob> =>
  new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('The browser could not encode this image.'))), type, quality);
  });

export type RenderImageOptions = {
  type: ImageOutputFormat;
  quality?: number;
  maxWidth?: number;
  maxHeight?: number;
  background?: string;
  rotation?: 0 | 90 | 180 | 270;
  crop?: { left: number; top: number; right: number; bottom: number };
};

export const renderImage = async (file: File, options: RenderImageOptions): Promise<Blob> => {
  const decoded = await decodeImage(file);
  try {
    const crop = options.crop ?? { left: 0, top: 0, right: 0, bottom: 0 };
    const left = Math.min(.45, Math.max(0, crop.left));
    const top = Math.min(.45, Math.max(0, crop.top));
    const right = Math.min(.45, Math.max(0, crop.right));
    const bottom = Math.min(.45, Math.max(0, crop.bottom));
    const sourceX = Math.round(decoded.width * left);
    const sourceY = Math.round(decoded.height * top);
    const sourceWidth = Math.max(1, decoded.width - sourceX - Math.round(decoded.width * right));
    const sourceHeight = Math.max(1, decoded.height - sourceY - Math.round(decoded.height * bottom));
    const scale = Math.min(1, (options.maxWidth ?? sourceWidth) / sourceWidth, (options.maxHeight ?? sourceHeight) / sourceHeight);
    const baseWidth = Math.max(1, Math.round(sourceWidth * scale));
    const baseHeight = Math.max(1, Math.round(sourceHeight * scale));
    const rotate = options.rotation ?? 0;
    const swap = rotate === 90 || rotate === 270;
    const canvas = document.createElement('canvas');
    canvas.width = swap ? baseHeight : baseWidth;
    canvas.height = swap ? baseWidth : baseHeight;
    const context = canvas.getContext('2d', { alpha: options.type !== 'image/jpeg' });
    if (!context) throw new Error('Canvas processing is unavailable in this browser.');
    if (options.type === 'image/jpeg' || options.background) {
      context.fillStyle = options.background ?? '#ffffff';
      context.fillRect(0, 0, canvas.width, canvas.height);
    }
    context.translate(canvas.width / 2, canvas.height / 2);
    context.rotate((rotate * Math.PI) / 180);
    context.drawImage(decoded.image, sourceX, sourceY, sourceWidth, sourceHeight, -baseWidth / 2, -baseHeight / 2, baseWidth, baseHeight);
    return await canvasToBlob(canvas, options.type, options.quality);
  } finally {
    URL.revokeObjectURL(decoded.url);
  }
};

export type CompressionResult = { blob: Blob; width: number; height: number; quality: number; reachedTarget: boolean };

export const compressImageToTarget = async (
  file: File,
  targetBytes: number,
  initialQuality: number,
  maxWidth?: number,
  maxHeight?: number
): Promise<CompressionResult> => {
  const decoded = await decodeImage(file);
  try {
    const initialScale = Math.min(1, (maxWidth ?? decoded.width) / decoded.width, (maxHeight ?? decoded.height) / decoded.height);
    let width = Math.max(1, Math.round(decoded.width * initialScale));
    let height = Math.max(1, Math.round(decoded.height * initialScale));
    let bestUnder: { blob: Blob; quality: number; width: number; height: number } | null = null;
    let bestOver: { blob: Blob; quality: number; width: number; height: number } | null = null;

    for (let dimensionPass = 0; dimensionPass < 8; dimensionPass += 1) {
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Canvas processing is unavailable in this browser.');
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, width, height);
      context.imageSmoothingEnabled = true;
      context.imageSmoothingQuality = 'high';
      context.drawImage(decoded.image, 0, 0, width, height);

      let low = 0.08;
      let high = Math.min(0.98, Math.max(0.1, initialQuality));
      for (let qualityPass = 0; qualityPass < 9; qualityPass += 1) {
        const quality = (low + high) / 2;
        const candidate = await canvasToBlob(canvas, 'image/jpeg', quality);
        if (candidate.size <= targetBytes) {
          if (!bestUnder || candidate.size > bestUnder.blob.size) bestUnder = { blob: candidate, quality, width, height };
          low = quality;
        } else {
          if (!bestOver || candidate.size < bestOver.blob.size) bestOver = { blob: candidate, quality, width, height };
          high = quality;
        }
      }
      if (bestUnder) return { blob: bestUnder.blob, width: bestUnder.width, height: bestUnder.height, quality: bestUnder.quality, reachedTarget: true };
      const longEdge = Math.max(width, height);
      if (longEdge <= 320) break;
      const scale = Math.max(320 / longEdge, .82);
      const nextWidth = Math.max(1, Math.round(width * scale));
      const nextHeight = Math.max(1, Math.round(height * scale));
      if (nextWidth === width && nextHeight === height) break;
      width = nextWidth;
      height = nextHeight;
    }
    if (!bestOver) throw new Error('No compressed image could be produced.');
    return { blob: bestOver.blob, width: bestOver.width, height: bestOver.height, quality: bestOver.quality, reachedTarget: false };
  } finally {
    URL.revokeObjectURL(decoded.url);
  }
};

export const imagesToPdf = async (files: File[], pageSize: 'a4' | 'letter' | 'fit', marginMm: number): Promise<Blob> => {
  if (files.length === 0) throw new Error('Add at least one image.');
  if (files.length > 50) throw new Error('A PDF can contain at most 50 images in one browser session.');
  let pdf: jsPDF | null = null;
  for (let index = 0; index < files.length; index += 1) {
    const decoded = await decodeImage(files[index]);
    try {
      const orientation = decoded.width > decoded.height ? 'landscape' : 'portrait';
      const format = pageSize === 'fit' ? [decoded.width * 0.264583, decoded.height * 0.264583] : pageSize;
      if (!pdf) pdf = new jsPDF({ orientation, unit: 'mm', format, compress: true });
      else pdf.addPage(format, orientation);
      const pageWidth = pdf.internal.pageSize.getWidth();
      const pageHeight = pdf.internal.pageSize.getHeight();
      const scale = Math.min((pageWidth - marginMm * 2) / decoded.width, (pageHeight - marginMm * 2) / decoded.height);
      const width = decoded.width * scale;
      const height = decoded.height * scale;
      const x = (pageWidth - width) / 2;
      const y = (pageHeight - height) / 2;
      pdf.addImage(decoded.image, 'JPEG', x, y, width, height, undefined, 'FAST');
    } finally {
      URL.revokeObjectURL(decoded.url);
    }
  }
  if (!pdf) throw new Error('No PDF could be created.');
  return pdf.output('blob');
};
