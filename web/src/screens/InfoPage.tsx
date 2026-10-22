import { useParams } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

const content = {
  privacy: {
    title: 'Privacy policy',
    intro: 'FileFlow processes documents on your device. Conversion does not require an account, database or processing server.',
    sections: [
      ['File processing', 'All tool screens process locally. PDF-to-Word reconstructs editable text and simple tables with approximate layout. PDF-to-Excel estimates aligned text columns for review. Scanned text requires OCR, which is not bundled. Word-to-PDF supports simple text reflow only. Browser PDF compression optimizes structure without recompressing images. Documents are not sent to a conversion service.'],
      ['History', 'Recent activity stores filenames, operation names, dates and output sizes in browser local storage. Output retention is off by default. If enabled in Settings, generated files are kept in this browser using IndexedDB for Save again. Clear recent history removes metadata and requests deletion of retained outputs; browser storage can also be cleared through browser settings.'],
      ['Network use', 'The app shell and processing assets load from this site and can be cached for offline use. Optional Supabase account login uses its configured authentication service; tools do not require login. Processing libraries do not upload your files.']
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
