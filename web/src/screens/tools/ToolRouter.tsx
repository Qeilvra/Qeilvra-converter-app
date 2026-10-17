import { lazy, Suspense } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { getTool } from '../../data/tools';

const DocumentConverter = lazy(() => import('./DocumentConverter').then((module) => ({ default: module.DocumentConverter })));
const ImageCompressor = lazy(() => import('./ImageCompressor').then((module) => ({ default: module.ImageCompressor })));
const ImageConverter = lazy(() => import('./ImageConverter').then((module) => ({ default: module.ImageConverter })));
const ImageToPdf = lazy(() => import('./ImageToPdf').then((module) => ({ default: module.ImageToPdf })));
const PdfCompressor = lazy(() => import('./PdfCompressor').then((module) => ({ default: module.PdfCompressor })));
const PdfEditor = lazy(() => import('./PdfEditor').then((module) => ({ default: module.PdfEditor })));
const PdfToExcel = lazy(() => import('./PdfToExcel').then((module) => ({ default: module.PdfToExcel })));
const PrepareUpload = lazy(() => import('./PrepareUpload').then((module) => ({ default: module.PrepareUpload })));

export function ToolRouter() {
  const { toolId } = useParams();
  const tool = getTool(toolId);
  if (!tool) return <Navigate to="/tools" replace />;
  let screen;
  switch (tool.id) {
    case 'pdf-to-excel': screen = <PdfToExcel tool={tool} />; break;
    case 'pdf-to-word': screen = <DocumentConverter tool={tool} direction="pdf-word" />; break;
    case 'word-to-pdf': screen = <DocumentConverter tool={tool} direction="word-pdf" />; break;
    case 'image-to-pdf': screen = <ImageToPdf tool={tool} />; break;
    case 'image-converter': screen = <ImageConverter tool={tool} />; break;
    case 'compress-image': screen = <ImageCompressor tool={tool} />; break;
    case 'compress-pdf': screen = <PdfCompressor tool={tool} />; break;
    case 'scanner': screen = <ImageToPdf tool={tool} scanner />; break;
    case 'pdf-editor': screen = <PdfEditor tool={tool} />; break;
    case 'prepare-upload': screen = <PrepareUpload tool={tool} />; break;
  }
  return <Suspense fallback={<div className="page"><div className="empty-state"><div className="loading-ring" /><h2>Loading this tool</h2><p>Processing engines are loaded only when needed.</p></div></div>}>{screen}</Suspense>;
}
