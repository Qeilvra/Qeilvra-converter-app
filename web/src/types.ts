export type ToolId =
  | 'pdf-to-excel'
  | 'pdf-to-word'
  | 'word-to-pdf'
  | 'image-to-pdf'
  | 'image-converter'
  | 'compress-image'
  | 'compress-pdf'
  | 'scanner'
  | 'pdf-editor'
  | 'prepare-upload';

export type RecentItem = {
  id: string;
  operation: string;
  sourceName: string;
  outputName: string;
  outputSize: number;
  createdAt: string;
  outputRetained?: boolean;
};

export type ProcessingState = 'idle' | 'reading' | 'processing' | 'success' | 'error';

export type TableCell = {
  value: string;
  uncertain: boolean;
};

export type ExtractedTable = {
  id: string;
  page: number;
  rows: TableCell[][];
  columnTypes?: Array<'text' | 'number' | 'date'>;
};

export type ImageOutputFormat = 'image/jpeg' | 'image/png' | 'image/webp';
