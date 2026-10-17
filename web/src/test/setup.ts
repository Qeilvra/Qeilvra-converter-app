const readBlob = (blob: Blob, method: 'arrayBuffer' | 'text'): Promise<ArrayBuffer | string> => new Promise((resolve, reject) => {
  const reader = new FileReader();
  reader.onerror = () => reject(reader.error ?? new Error('Test Blob read failed.'));
  reader.onload = () => resolve(reader.result as ArrayBuffer | string);
  if (method === 'arrayBuffer') reader.readAsArrayBuffer(blob);
  else reader.readAsText(blob);
});

if (typeof Blob.prototype.arrayBuffer !== 'function') {
  Object.defineProperty(Blob.prototype, 'arrayBuffer', {
    configurable: true,
    value(this: Blob) { return readBlob(this, 'arrayBuffer') as Promise<ArrayBuffer>; }
  });
}

if (typeof Blob.prototype.text !== 'function') {
  Object.defineProperty(Blob.prototype, 'text', {
    configurable: true,
    value(this: Blob) { return readBlob(this, 'text') as Promise<string>; }
  });
}
