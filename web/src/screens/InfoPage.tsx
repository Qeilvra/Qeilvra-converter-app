import { useParams } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

const content = {
  privacy: {
    title: 'Privacy policy',
    intro: 'FileFlow keeps lightweight tools on your device and clearly labels operations that need the secure processing service.',
    sections: [
      ['File processing', 'Image tools, scanning and PDF page editing run locally. PDF and Office conversion, PDF compression and table recognition upload a temporary copy over HTTPS to the configured FileFlow worker, which deletes its job workspace after the response.'],
      ['History', 'Recent activity contains filenames, operation names, dates and output sizes. It is stored in browser local storage and can be cleared at any time. File contents are not placed in history.'],
      ['Network use', 'The app shell may update from its hosting origin. Service conversion does not require an account, permanent document database or public storage URL.']
    ]
  },
  terms: {
    title: 'Terms & limitations',
    intro: 'Use FileFlow only with files you have the right to process, and review important outputs before submission.',
    sections: [
      ['Conversion quality', 'PDF and Office formats represent layouts differently. Even layout-aware conversion can require correction, especially for unusual fonts, complex drawing layers, scans or password-protected documents.'],
      ['Compression', 'Some already-optimized files cannot be made smaller without reducing quality. FileFlow reports the measured result and keeps the original when its optimized candidate is not smaller.'],
      ['No official portal claims', 'Prepare for Upload presets are editable starting points. Always compare the result with the current requirements of the destination portal.']
    ]
  },
  help: {
    title: 'Help',
    intro: 'For the best results, start with a clear, unencrypted source file and keep the original.',
    sections: [
      ['Scanned PDFs', 'PDF to Excel does not invent data from image-only pages. Configure an approved OCR/table-recognition engine or create a searchable PDF first.'],
      ['Target file sizes', 'Image compression searches quality and dimensions to fit under your maximum. Extremely small targets can make documents unreadable; inspect the preview before use.'],
      ['Saving files', 'On supported mobile browsers, Share opens the system share sheet. Otherwise, FileFlow downloads the file through your browser.']
    ]
  }
} as const;

export function InfoPage() {
  const { page = 'help' } = useParams();
  const info = content[page as keyof typeof content] ?? content.help;
  return <div className="page page--narrow"><PageHeader title={info.title} /><div className="prose-card"><p className="lead">{info.intro}</p>{info.sections.map(([title, text]) => <section key={title}><h2>{title}</h2><p>{text}</p></section>)}</div></div>;
}
