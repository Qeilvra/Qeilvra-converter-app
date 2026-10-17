import { useParams } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

const content = {
  privacy: {
    title: 'Privacy policy',
    intro: 'FileFlow’s browser tools are designed to process files locally on your device.',
    sections: [
      ['File processing', 'Files selected in FileFlow are read by your browser and are not uploaded by the current web application. Generated downloads remain under your control.'],
      ['History', 'Recent activity contains filenames, operation names, dates and output sizes. It is stored in browser local storage and can be cleared at any time. File contents are not placed in history.'],
      ['Network use', 'The installed app shell may update from its hosting origin. Document processing does not require an account or analytics service.']
    ]
  },
  terms: {
    title: 'Terms & limitations',
    intro: 'Use FileFlow only with files you have the right to process, and review important outputs before submission.',
    sections: [
      ['Conversion quality', 'PDF and Office formats represent layouts differently. Text and table extraction is best-effort and may require correction, especially for complex, scanned or password-protected documents.'],
      ['Compression', 'Some already-optimized files cannot be made smaller without reducing quality. FileFlow reports the measured result and does not claim a target was met when it was not.'],
      ['No official portal claims', 'Prepare for Upload presets are editable starting points. Always compare the result with the current requirements of the destination portal.']
    ]
  },
  help: {
    title: 'Help',
    intro: 'For the best results, start with a clear, unencrypted source file and keep the original.',
    sections: [
      ['Scanned PDFs', 'PDF to Excel and PDF to Word need selectable text. A PDF containing only page images requires OCR, which is not included in this browser release.'],
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
