import { Archive, ChevronRight, Eraser, FileQuestion, Languages, LockKeyhole, Moon, Scale, SunMedium } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { clearHistory, readSettings, saveSettings, type AppSettings } from '../lib/files';

export function SettingsPage() {
  const [settings, setState] = useState<AppSettings>(readSettings);
  const update = (next: AppSettings) => { setState(next); saveSettings(next); document.documentElement.dataset.theme = next.theme; };
  return (
    <div className="page settings-page">
      <header className="simple-header"><span className="eyebrow">Make it yours</span><h1>Settings</h1><p>FileFlow stores these preferences only in this browser.</p></header>
      <section className="settings-section"><h2>Appearance</h2><div className="segmented-control segmented-control--three">
        {(['light', 'dark', 'system'] as const).map((theme) => <button key={theme} className={settings.theme === theme ? 'active' : ''} onClick={() => update({ ...settings, theme })}>{theme === 'light' ? <SunMedium /> : <Moon />} {theme}</button>)}
      </div></section>
      <section className="settings-section"><h2>Language</h2><label className="setting-row"><span className="setting-row__icon"><Languages /></span><span><strong>App language</strong><small>Urdu interface is prepared for a future translation.</small></span><select value={settings.language} onChange={(event) => update({ ...settings, language: event.target.value as AppSettings['language'] })}><option value="en">English</option><option value="ur" disabled>Urdu — coming soon</option></select></label></section>
      <section className="settings-section"><h2>Local output retention</h2><label className="setting-row"><span className="setting-row__icon"><Archive /></span><span><strong>Keep generated files in Recent</strong><small>Off by default. When enabled, future outputs are stored only in this browser so they can be saved again.</small></span><input type="checkbox" checked={settings.retainOutputs} onChange={(event) => update({ ...settings, retainOutputs: event.target.checked })} /></label></section>
      <section className="settings-section"><h2>Privacy &amp; support</h2>
        <Link className="setting-row" to="/info/privacy"><span className="setting-row__icon"><LockKeyhole /></span><span><strong>Privacy policy</strong><small>Which tools are local and which use the secure service</small></span><ChevronRight /></Link>
        <Link className="setting-row" to="/info/terms"><span className="setting-row__icon"><Scale /></span><span><strong>Terms &amp; limitations</strong><small>What each conversion can and cannot do</small></span><ChevronRight /></Link>
        <Link className="setting-row" to="/info/help"><span className="setting-row__icon"><FileQuestion /></span><span><strong>Help</strong><small>Tips for reliable output</small></span><ChevronRight /></Link>
        <button className="setting-row setting-row--button" type="button" onClick={() => clearHistory()}><span className="setting-row__icon"><Eraser /></span><span><strong>Clear recent history</strong><small>Deletes locally stored activity metadata</small></span><ChevronRight /></button>
      </section>
      <p className="version-label">FileFlow web · version 0.1.0</p>
    </div>
  );
}
