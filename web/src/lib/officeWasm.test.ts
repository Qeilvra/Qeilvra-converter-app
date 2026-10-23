import { afterEach, expect, it, vi } from 'vitest';
import { renderDocxOffline } from './officeWasm';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
const source = {size:20,name:'original.docx',arrayBuffer:async()=>new ArrayBuffer(20)} as File;
it('never reads the document or contacts a service in an unsupported browser context', async () => {
  vi.stubGlobal('crossOriginIsolated',false);
  const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);
  const read=vi.fn();
  await expect(renderDocxOffline({...source,arrayBuffer:read} as unknown as File)).rejects.toThrow('No text-only PDF was created');
  expect(read).not.toHaveBeenCalled(); expect(fetcher).not.toHaveBeenCalled();
});
it('rejects an HTML SPA fallback as a missing runtime before reading source bytes', async () => {
  vi.stubGlobal('crossOriginIsolated',true);
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('<html>FileFlow</html>',{status:200,headers:{'content-type':'text/html'}})));
  const read=vi.fn();
  await expect(renderDocxOffline({...source,arrayBuffer:read} as unknown as File)).rejects.toThrow('not packaged');
  expect(read).not.toHaveBeenCalled();
  expect(document.querySelector('iframe')).toBeNull();
});
it('cancellation before startup does not open an engine frame', async () => {
  vi.stubGlobal('crossOriginIsolated',true);
  const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);
  const controller=new AbortController();controller.abort();
  await expect(renderDocxOffline(source,[],undefined,controller.signal)).rejects.toMatchObject({name:'AbortError'});
  expect(fetcher).not.toHaveBeenCalled();expect(document.querySelector('iframe')).toBeNull();
});
it('does not initialize the large engine on a reported low-memory device', async () => {
  vi.stubGlobal('crossOriginIsolated',true);
  vi.stubGlobal('navigator',{deviceMemory:2});
  const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);
  await expect(renderDocxOffline(source)).rejects.toThrow('low-memory devices');
  expect(fetcher).not.toHaveBeenCalled();
});
