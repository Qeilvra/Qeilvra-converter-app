import type { RecentItem } from '../types';

const HISTORY_KEY = 'fileflow.history.v1';
const SETTINGS_KEY = 'fileflow.settings.v1';
const OUTPUT_DB = 'fileflow.outputs.v1';
const OUTPUT_STORE = 'outputs';
let retentionGeneration = 0;
let retentionQueue: Promise<void> = Promise.resolve();

export const formatBytes = (bytes: number): string => {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value >= 100 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`;
};

export const safeBaseName = (name: string): string => {
  const base = name.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9._-]+/g, '-').replace(/^-+|-+$/g, '');
  return base || 'fileflow-output';
};

export const downloadBlob = (blob: Blob, filename: string): void => {
  if (blob.size === 0) throw new Error('This output is empty. Process the file again before saving.');
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
};

export const canShareFile = (blob: Blob, filename: string): boolean => {
  const file = new File([blob], filename, { type: blob.type });
  try {
    return typeof navigator.share === 'function' && Boolean(navigator.canShare?.({ files: [file] }));
  } catch {
    return false;
  }
};

export const shareBlob = async (blob: Blob, filename: string): Promise<void> => {
  const file = new File([blob], filename, { type: blob.type });
  if (!canShareFile(blob, filename)) throw new Error('File sharing is not supported by this browser. Use Save instead.');
  await navigator.share({ files: [file], title: `Share ${filename}` });
};

export const readHistory = (): RecentItem[] => {
  try {
    const value = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]') as unknown;
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is RecentItem => {
      if (!item || typeof item !== 'object') return false;
      const entry = item as Partial<RecentItem>;
      return typeof entry.id === 'string' && typeof entry.operation === 'string' &&
        typeof entry.sourceName === 'string' && typeof entry.outputName === 'string' &&
        typeof entry.outputSize === 'number' && Number.isFinite(entry.outputSize) &&
        typeof entry.createdAt === 'string' && Number.isFinite(Date.parse(entry.createdAt));
    });
  } catch {
    return [];
  }
};

type StoredOutput = { id: string; filename: string; blob: Blob };

const openOutputDb = (): Promise<IDBDatabase> => new Promise((resolve, reject) => {
  if (!('indexedDB' in window)) {
    reject(new Error('Persistent browser storage is unavailable.'));
    return;
  }
  const request = indexedDB.open(OUTPUT_DB, 1);
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains(OUTPUT_STORE)) request.result.createObjectStore(OUTPUT_STORE, { keyPath: 'id' });
  };
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error ?? new Error('Could not open output storage.'));
});

const storeOutput = async (entry?: RecentItem, blob?: Blob): Promise<void> => {
  const db = await openOutputDb();
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(OUTPUT_STORE, 'readwrite');
      const outputStore = transaction.objectStore(OUTPUT_STORE);
      if (entry && blob) outputStore.put({ id: entry.id, filename: entry.outputName, blob } satisfies StoredOutput);
      const retainedIds = new Set(readHistory().map((item) => item.id));
      const cursorRequest = outputStore.openCursor();
      cursorRequest.onsuccess = () => {
        const cursor = cursorRequest.result;
        if (!cursor) return;
        if (!retainedIds.has(String(cursor.key))) cursor.delete();
        cursor.continue();
      };
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error ?? new Error('Could not retain this output.'));
      transaction.onabort = () => reject(transaction.error ?? new Error('Output retention was cancelled.'));
    });
  } finally {
    db.close();
  }
};

export const readStoredOutput = async (id: string): Promise<StoredOutput | null> => {
  try {
    const db = await openOutputDb();
    try {
      return await new Promise<StoredOutput | null>((resolve, reject) => {
        const request = db.transaction(OUTPUT_STORE, 'readonly').objectStore(OUTPUT_STORE).get(id);
        request.onsuccess = () => resolve((request.result as StoredOutput | undefined) ?? null);
        request.onerror = () => reject(request.error ?? new Error('Could not read this output.'));
      });
    } finally {
      db.close();
    }
  } catch {
    return null;
  }
};

const clearStoredOutputs = async (): Promise<void> => {
  try {
    const db = await openOutputDb();
    try {
      await new Promise<void>((resolve, reject) => {
        const transaction = db.transaction(OUTPUT_STORE, 'readwrite');
        transaction.objectStore(OUTPUT_STORE).clear();
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => reject(transaction.error ?? new Error('Could not clear retained outputs.'));
      });
    } finally {
      db.close();
    }
  } catch {
    // Metadata clearing must still work when IndexedDB is unavailable.
  }
};

export const addHistory = (item: Omit<RecentItem, 'id' | 'createdAt' | 'outputRetained'>, blob?: Blob): RecentItem => {
  const entry: RecentItem = {
    ...item,
    id: crypto.randomUUID(),
    createdAt: new Date().toISOString(),
    outputRetained: false
  };
  const previous = readHistory();
  const next = [entry, ...previous].slice(0, 30);
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(next)); }
  catch { return entry; } // Storage failure must not invalidate a successfully generated file.
  window.dispatchEvent(new Event('fileflow-history'));
  if (blob && readSettings().retainOutputs) {
    const generation = retentionGeneration;
    retentionQueue = retentionQueue.then(async () => {
      if (generation !== retentionGeneration || !readHistory().some((item) => item.id === entry.id)) return;
      await storeOutput(entry, blob);
      if (generation !== retentionGeneration) return;
      const current = readHistory();
      const retained = current.map((record) => record.id === entry.id ? { ...record, outputRetained: true } : record);
      localStorage.setItem(HISTORY_KEY, JSON.stringify(retained));
      window.dispatchEvent(new Event('fileflow-history'));
    }).catch(() => {
      // Quota and private-mode failures leave a truthful metadata-only record.
    });
  } else if (previous.some((item) => item.outputRetained)) {
    retentionQueue = retentionQueue.then(() => storeOutput()).catch(() => undefined);
  }
  return entry;
};

export const clearHistory = (): void => {
  retentionGeneration += 1;
  try { localStorage.removeItem(HISTORY_KEY); } catch { /* Storage may be disabled. */ }
  window.dispatchEvent(new Event('fileflow-history'));
  // Serialize with in-flight writes so Clear cannot be followed by a stale retained file.
  retentionQueue = retentionQueue.then(clearStoredOutputs, clearStoredOutputs);
};

export type AppSettings = { theme: 'light' | 'dark' | 'system'; language: 'en' | 'ur'; retainOutputs: boolean };

const DEFAULT_SETTINGS: AppSettings = { theme: 'light', language: 'en', retainOutputs: false };

export const readSettings = (): AppSettings => {
  try {
    const stored = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? '{}') as Partial<AppSettings>;
    return {
      theme: stored.theme === 'dark' || stored.theme === 'system' ? stored.theme : 'light',
      language: stored.language === 'ur' ? 'ur' : 'en',
      retainOutputs: stored.retainOutputs === true
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
};

export const saveSettings = (settings: AppSettings): void => {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  window.dispatchEvent(new Event('fileflow-settings'));
};

export const sniffFileType = async (file: File): Promise<'pdf' | 'jpeg' | 'png' | 'webp' | 'docx' | 'unknown'> => {
  const bytes = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  const ascii = String.fromCharCode(...bytes);
  if (ascii.startsWith('%PDF-')) return 'pdf';
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'jpeg';
  if (bytes[0] === 0x89 && ascii.slice(1, 4) === 'PNG') return 'png';
  if (ascii.slice(0, 4) === 'RIFF' && ascii.slice(8, 12) === 'WEBP') return 'webp';
  if (ascii.slice(0, 2) === 'PK' && file.name.toLowerCase().endsWith('.docx')) return 'docx';
  return 'unknown';
};

export const validateFile = async (file: File, allowed: Array<Awaited<ReturnType<typeof sniffFileType>>>, maxMb = 50): Promise<void> => {
  if (!file) throw new Error('Choose a file first.');
  if (file.size === 0) throw new Error('This file is empty. Choose a different file.');
  if (file.size > maxMb * 1024 * 1024) throw new Error(`This file is larger than the ${maxMb} MB browser limit.`);
  const detected = await sniffFileType(file);
  if (!allowed.includes(detected)) throw new Error('The file contents do not match a supported format.');
};
