export interface ContextCapsule {
  id: string;
  title: string;
  project: string;
  description?: string;
  rawContent: string;
  compressedContent: string;
  compressionStrategy: 'SEMANTIC_DENSE' | 'STATE_KV' | 'MARKDOWN_OUTLINE' | 'AI_DEEP_DISTILL';
  originalTokens: number;
  compressedTokens: number;
  compressionRatio: number;
  tags?: string;
  shareSlug: string;
  isPublic: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ContextCollaborator {
  id: string;
  contextId: string;
  authorName: string;
  note: string;
  createdAt: string;
}

export interface CreateContextRequest {
  title: string;
  project?: string;
  description?: string;
  rawContent: string;
  compressionStrategy?: string;
  tags?: string;
  isPublic?: boolean;
}

export interface UpdateContextRequest {
  title?: string;
  project?: string;
  description?: string;
  rawContent?: string;
  compressedContent?: string;
  compressionStrategy?: string;
  tags?: string;
  isPublic?: boolean;
}

export interface CompressPreviewRequest {
  title?: string;
  project?: string;
  rawContent: string;
  compressionStrategy?: string;
}

export interface CompressPreviewResponse {
  compressedContent: string;
  originalTokens: number;
  compressedTokens: number;
  compressionRatio: number;
  tokensSaved: number;
  strategyUsed: string;
}

export interface StatsSummary {
  totalContexts: number;
  totalTokensSaved: number;
  averageCompressionRatio: number;
  projects: string[];
}
