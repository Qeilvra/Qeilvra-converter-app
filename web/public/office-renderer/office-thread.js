// Build preparation packages this module beside its pinned helper and WASM.
// Relative imports keep every worker on the same runtime version offline too.
import { ZetaHelperThread } from './zetaHelper.js';
const helper = new ZetaHelperThread();
const { css, zetajs } = helper;
const property = (Name, Value) => new css.beans.PropertyValue({ Name, Value });
helper.thrPort.onmessage = event => {
  if (event.data.cmd !== 'convert') return;
  let document;
  try {
    document = helper.desktop.loadComponentFromURL('file:///tmp/fileflow-input.docx','_blank',0,[
      property('Hidden',true), property('ReadOnly',true),
      property('MacroExecutionMode',new zetajs.Any(zetajs.type.short,0)), // MacroExecMode.NEVER_EXECUTE
      property('UpdateDocMode',new zetajs.Any(zetajs.type.short,0)), // UpdateDocMode.NO_UPDATE
    ]);
    if (!document) throw Error('LibreOffice could not open this DOCX. It may be encrypted or damaged.');
    document.storeToURL('file:///tmp/fileflow-output.pdf',[
      property('Overwrite',true), property('FilterName','writer_pdf_Export'),
    ]);
    document.close(true); document = null;
    helper.thrPort.postMessage({cmd:'converted'});
  } catch (error) {
    let message;
    try { message = zetajs.catchUnoException(error).Message; } catch { message = error?.message; }
    helper.thrPort.postMessage({cmd:'failed', message:message || String(error)});
  } finally { if(document)try{document.close(true)}catch{} }
};
helper.thrPort.postMessage({cmd:'ready'});
