import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ContextCapsule,
  ContextCollaborator,
  CreateContextRequest,
  UpdateContextRequest,
  CompressPreviewRequest,
  CompressPreviewResponse,
  StatsSummary
} from '../models/context.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ContextService {
  private readonly baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getContexts(search?: string, project?: string): Observable<ContextCapsule[]> {
    let params = new HttpParams();
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    if (project && project !== 'ALL') {
      params = params.set('project', project);
    }
    return this.http.get<ContextCapsule[]>(`${this.baseUrl}/contexts`, { params });
  }

  getContext(id: string): Observable<ContextCapsule> {
    return this.http.get<ContextCapsule>(`${this.baseUrl}/contexts/${id}`);
  }

  getBySlug(slug: string): Observable<ContextCapsule> {
    return this.http.get<ContextCapsule>(`${this.baseUrl}/public/c/${slug}`);
  }

  createContext(req: CreateContextRequest): Observable<ContextCapsule> {
    return this.http.post<ContextCapsule>(`${this.baseUrl}/contexts`, req);
  }

  updateContext(id: string, req: UpdateContextRequest): Observable<ContextCapsule> {
    return this.http.put<ContextCapsule>(`${this.baseUrl}/contexts/${id}`, req);
  }

  deleteContext(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/contexts/${id}`);
  }

  forkContext(id: string): Observable<ContextCapsule> {
    return this.http.post<ContextCapsule>(`${this.baseUrl}/contexts/${id}/fork`, {});
  }

  previewCompression(req: CompressPreviewRequest): Observable<CompressPreviewResponse> {
    return this.http.post<CompressPreviewResponse>(`${this.baseUrl}/contexts/preview-compression`, req);
  }

  getLmStudioStatus(): Observable<{ online: boolean; port: number; url: string; provider?: string; model?: string }> {
    return this.http.get<{ online: boolean; port: number; url: string; provider?: string; model?: string }>(`${this.baseUrl}/contexts/lmstudio-status`);
  }

  getNotes(contextId: string): Observable<ContextCollaborator[]> {
    return this.http.get<ContextCollaborator[]>(`${this.baseUrl}/contexts/${contextId}/notes`);
  }

  addNote(contextId: string, authorName: string, note: string): Observable<ContextCollaborator> {
    return this.http.post<ContextCollaborator>(`${this.baseUrl}/contexts/${contextId}/notes`, {
      authorName,
      note
    });
  }

  getStats(): Observable<StatsSummary> {
    return this.http.get<StatsSummary>(`${this.baseUrl}/stats`);
  }

  getRawUrl(shareSlug: string): string {
    return `${this.baseUrl}/public/c/${shareSlug}/raw`;
  }

  getPromptUrl(shareSlug: string): string {
    return `${this.baseUrl}/public/c/${shareSlug}/prompt`;
  }

  getMcpUrl(shareSlug: string): string {
    return `${this.baseUrl}/public/c/${shareSlug}/mcp`;
  }

  getShareUrl(shareSlug: string): string {
    return `${window.location.origin}/c/${shareSlug}`;
  }
}
