import { useParams } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

const content = {
  privacy: {
    title: 'Privacy policy',
    intro: 'FileFlow keeps lightweight tools on your device and clearly labels operations that need the secure processing service.',
    sections: [
      ['File processing', 'Image tools, scanning and PDF page editing run locally. PDF and Office conversion, PDF compression and table recognition upload a temporary copy to the configured FileFlow worker. Production service URLs must use HTTPS. The worker attempts job workspace cleanup after each request; immediate deletion is not guaranteed.'],
      ['History', 'Recent activity stores filenames, operation names, dates and output sizes in browser local storage. Output retention is off by default. If enabled in Settings, generated files are kept in this browser using IndexedDB for Save again. Clear recent history removes metadata and requests deletion of retained outputs; browser storage can also be cleared through browser settings.'],
      ['Network use', 'The app shell may update from its hosting origin. Service conversion does not require an account, permanent document database or public storage URL.']
    ]
  },
  terms: {
    title: 'Terms & limitations',
    intro: 'Use FileFlow only with files you have the right to process, and review important outputs before submission.',
    sections: [
      ['Conversion quality', 'PDF and Office formats represent layouts differently. Even layout-aware conversion can require correction, especially for unusual fonts, complex drawing layers, scans or password-protected documents.'],
      ['Compression', 'Some already-optimized files cannot be made smaller without reducing quality. The PDF compressor keeps the original when its candidate is not smaller. Image compression reports actual size and warns when re-encoding does not reduce it.'],
      ['No official portal claims', 'Prepare for Upload presets are editable starting points. Always compare the result with the current requirements of the destination portal.']
    ]
  },
  help: {
    title: 'Help',
    intro: 'For the best results, start with a clear, unencrypted source file and keep the original.',
    sections: [
      ['Scanned PDFs', 'OCR is not included. PDF to Excel needs selectable text; create a searchable PDF with a trusted OCR tool before extracting tables from an image-only scan.'],
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
