import type { ComponentType } from 'react';
import {
  Camera,
  FileArchive,
  FileImage,
  FilePenLine,
  FileSpreadsheet,
  FileText,
  ImageDown,
  Images,
  ScanLine,
  UploadCloud
} from 'lucide-react';
import type { ToolId } from '../types';

export type ToolDefinition = {
  id: ToolId;
  name: string;
  shortName: string;
  description: string;
  Icon: ComponentType<{ size?: number; strokeWidth?: number }>;
  color: 'red' | 'blue' | 'green' | 'purple' | 'orange' | 'cyan';
  accept: string;
};

export const tools: ToolDefinition[] = [
  { id: 'pdf-to-excel', name: 'PDF to Excel', shortName: 'PDF to Excel', description: 'Extract tables to .xlsx', Icon: FileSpreadsheet, color: 'red', accept: '.pdf,application/pdf' },
  { id: 'pdf-to-word', name: 'PDF to Word', shortName: 'PDF to Word', description: 'Layout-aware editable DOCX', Icon: FileText, color: 'blue', accept: '.pdf,application/pdf' },
  { id: 'word-to-pdf', name: 'Word to PDF', shortName: 'Word to PDF', description: 'Convert DOCX to PDF', Icon: FileArchive, color: 'cyan', accept: '.docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  { id: 'image-to-pdf', name: 'Image to PDF', shortName: 'Image to PDF', description: 'JPG, PNG, WebP to PDF', Icon: Images, color: 'purple', accept: 'image/jpeg,image/png,image/webp' },
  { id: 'image-converter', name: 'Image Converter', shortName: 'Image Convert', description: 'Change image format', Icon: FileImage, color: 'cyan', accept: 'image/jpeg,image/png,image/webp' },
  { id: 'compress-image', name: 'Image Compressor', shortName: 'Compress Image', description: 'Reach a target file size', Icon: ImageDown, color: 'green', accept: 'image/jpeg,image/png,image/webp' },
  { id: 'compress-pdf', name: 'PDF Compressor', shortName: 'Compress PDF', description: 'Optimize PDF structure locally', Icon: FileArchive, color: 'green', accept: '.pdf,application/pdf' },
  { id: 'scanner', name: 'Document Scanner', shortName: 'Scan Document', description: 'Detect, straighten and enhance', Icon: ScanLine, color: 'orange', accept: 'image/*' },
  { id: 'pdf-editor', name: 'PDF Editor', shortName: 'PDF Editor', description: 'Merge, split and rotate', Icon: FilePenLine, color: 'blue', accept: '.pdf,application/pdf' },
  { id: 'prepare-upload', name: 'Prepare for Upload', shortName: 'Prepare Upload', description: 'Size and format for forms', Icon: UploadCloud, color: 'purple', accept: 'image/jpeg,image/png,image/webp' }
];

export const popularToolIds: ToolId[] = ['pdf-to-excel', 'pdf-to-word', 'image-to-pdf', 'compress-image', 'scanner', 'pdf-editor'];
export const getTool = (id: string | undefined): ToolDefinition | undefined => tools.find((tool) => tool.id === id);

export const CameraIcon = Camera;
