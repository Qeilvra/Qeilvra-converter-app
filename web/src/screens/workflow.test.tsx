// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { ResultCard } from '../components/ResultCard';
import { tools } from '../data/tools';
import { SettingsPage } from './SettingsPage';
import { AuthProvider } from '../auth/AuthContext';

beforeEach(() => {
  localStorage.clear();
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:test') });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
});

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe('application routing', () => {
  it('opens every advertised web tool through a real route', async () => {
    render(<MemoryRouter initialEntries={['/tools']}><App /></MemoryRouter>);
    for (const tool of tools) expect(screen.getByRole('link', { name: new RegExp(`^${tool.shortName}`, 'i') })).toHaveAttribute('href', `/tools/${tool.id}`);
  });

  it('loads a deep-linked tool and exposes its file picker', async () => {
    await import('./tools/PdfToExcel');
    render(<MemoryRouter initialEntries={['/tools/pdf-to-excel']}><App /></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: 'PDF to Excel', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /choose file/i })).toBeEnabled();
  }, 15_000);
});

describe('save, share and privacy settings', () => {
  it('keeps Save as a download even when native sharing is available', () => {
    const share = vi.fn(async () => undefined);
    Object.defineProperty(navigator, 'share', { configurable: true, value: share });
    Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => true });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    render(<ResultCard blob={new Blob(['output'], { type: 'text/plain' })} filename="result.txt" />);
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    expect(click).toHaveBeenCalledOnce();
    expect(share).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent(/download requested/i);
  });

  it('stores output retention as an explicit opt-in', async () => {
    render(<MemoryRouter><AuthProvider><SettingsPage /></AuthProvider></MemoryRouter>);
    const toggle = screen.getByRole('checkbox', { name: /keep generated files/i });
    expect(toggle).not.toBeChecked();
    fireEvent.click(toggle);
    await waitFor(() => expect(JSON.parse(localStorage.getItem('fileflow.settings.v1') ?? '{}').retainOutputs).toBe(true));
  });
});
