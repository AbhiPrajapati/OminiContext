import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { ContextService } from '../../services/context.service';
import { ContextCapsule, ContextCollaborator } from '../../models/context.model';

@Component({
  selector: 'app-shared-context',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './shared-context.component.html',
  styleUrls: ['./shared-context.component.css']
})
export class SharedContextComponent implements OnInit {
  slug: string | null = null;
  capsule: ContextCapsule | null = null;
  notes: ContextCollaborator[] = [];
  activeTab: 'compressed' | 'prompt' | 'raw' | 'mcp' = 'compressed';

  isLoading = true;
  errorMessage = '';
  toastMessage = '';

  newNoteAuthor = '';
  newNoteContent = '';
  isAddingNote = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private contextService: ContextService
  ) {}

  ngOnInit(): void {
    this.slug = this.route.snapshot.paramMap.get('slug');
    if (this.slug) {
      this.loadSharedContext(this.slug);
    } else {
      this.errorMessage = 'No context share link specified.';
      this.isLoading = false;
    }
  }

  loadSharedContext(slug: string): void {
    this.isLoading = true;
    this.contextService.getBySlug(slug).subscribe({
      next: (data) => {
        this.capsule = data;
        this.isLoading = false;
        this.loadNotes(data.id);
      },
      error: () => {
        this.errorMessage = 'This AI Context Capsule was not found or is set to private.';
        this.isLoading = false;
      }
    });
  }

  loadNotes(contextId: string): void {
    this.contextService.getNotes(contextId).subscribe({
      next: (notes) => this.notes = notes,
      error: (err) => console.error('Failed to load notes', err)
    });
  }

  addNote(): void {
    if (!this.capsule || !this.newNoteContent.trim()) return;

    this.isAddingNote = true;
    const author = this.newNoteAuthor.trim() || 'Collaborator';
    this.contextService.addNote(this.capsule.id, author, this.newNoteContent.trim()).subscribe({
      next: (note) => {
        this.notes.unshift(note);
        this.newNoteContent = '';
        this.isAddingNote = false;
        this.showToast('Note posted to shared context!');
      },
      error: () => {
        this.isAddingNote = false;
        this.showToast('Failed to post note');
      }
    });
  }

  copyPrompt(): void {
    if (!this.capsule) return;
    const prompt = `=== OMNICONTEXT ACTIVE MEMORY [${this.capsule.title} | ${this.capsule.project} | v${this.capsule.version}] ===\n${this.capsule.compressedContent}\n=== INSTRUCTIONS FOR AI ===\nAbsorb the context above as ground-truth memory for our task. Respond confirmingly and proceed with the current active task.`;
    navigator.clipboard.writeText(prompt);
    this.showToast('Ready-to-inject AI Prompt copied to clipboard!');
  }

  copyRawUrl(): void {
    if (!this.capsule) return;
    const url = this.contextService.getRawUrl(this.capsule.shareSlug);
    navigator.clipboard.writeText(url);
    this.showToast('Raw AI Fetch URL copied!');
  }

  copyMcpResource(): void {
    if (!this.capsule) return;
    const mcp = JSON.stringify({
      uri: `context://${this.capsule.shareSlug}`,
      name: this.capsule.title,
      description: `Synced AI context for project ${this.capsule.project}`,
      mimeType: "text/plain",
      contents: {
        text: this.capsule.compressedContent,
        tokens: this.capsule.compressedTokens,
        version: this.capsule.version
      }
    }, null, 2);
    navigator.clipboard.writeText(mcp);
    this.showToast('MCP JSON schema copied!');
  }

  forkThisContext(): void {
    if (!this.capsule) return;
    this.contextService.forkContext(this.capsule.id).subscribe({
      next: (forked) => {
        this.showToast('Forked context to your workspace!');
        this.router.navigate(['/editor', forked.id]);
      },
      error: () => this.showToast('Failed to fork context')
    });
  }

  getPromptText(): string {
    if (!this.capsule) return '';
    return `=== OMNICONTEXT ACTIVE MEMORY [${this.capsule.title} | ${this.capsule.project} | v${this.capsule.version}] ===
${this.capsule.compressedContent}
=== INSTRUCTIONS FOR AI ===
Absorb the state, tech stack, and decisions above as ground-truth memory.
Maintain strict consistency with the decisions and constraints recorded.
Acknowledge this context briefly in one sentence and ask or proceed with the active task.`;
  }

  getMcpText(): string {
    if (!this.capsule) return '';
    return JSON.stringify({
      uri: `context://${this.capsule.shareSlug}`,
      name: this.capsule.title,
      description: `Synced AI context for project ${this.capsule.project}`,
      mimeType: "text/plain",
      contents: {
        text: this.capsule.compressedContent,
        tokens: this.capsule.compressedTokens,
        version: this.capsule.version
      }
    }, null, 2);
  }

  showToast(msg: string): void {
    this.toastMessage = msg;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = '';
      }
    }, 3500);
  }
}
