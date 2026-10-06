import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ContextService } from '../../services/context.service';
import { ContextCapsule, ContextCollaborator, CompressPreviewResponse } from '../../models/context.model';

@Component({
  selector: 'app-context-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './context-editor.component.html',
  styleUrls: ['./context-editor.component.css']
})
export class ContextEditorComponent implements OnInit, OnDestroy {
  contextId: string | null = null;
  isEditMode = false;
  isLoading = false;
  isSaving = false;
  isCompressing = false;

  // Form Fields
  title = '';
  project = 'General';
  description = '';
  rawContent = '';
  compressedContent = '';
  compressionStrategy: 'SEMANTIC_DENSE' | 'STATE_KV' | 'MARKDOWN_OUTLINE' | 'AI_DEEP_DISTILL' = 'SEMANTIC_DENSE';
  tags = '';
  isPublic = true;
  shareSlug = '';
  version = 1;
  isLmStudioOnline = false;
  aiProvider = 'LMSTUDIO';
  aiModel = '';

  // Live Token Stats
  originalTokens = 0;
  compressedTokens = 0;
  compressionRatio = 0;
  tokensSaved = 0;

  // Collaboration Notes
  notes: ContextCollaborator[] = [];
  newNoteAuthor = '';
  newNoteContent = '';
  isAddingNote = false;

  toastMessage = '';

  private rawContentSubject = new Subject<string>();
  private sub = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private contextService: ContextService
  ) {}

  ngOnInit(): void {
    this.checkLmStudioStatus();

    this.contextId = this.route.snapshot.paramMap.get('id');
    if (this.contextId) {
      this.isEditMode = true;
      this.loadContext(this.contextId);
    } else {
      // Default template for new context
      this.title = 'New AI Context Capsule';
      this.rawContent = `User: We are designing an API service with Spring Boot and Angular.
Decision: We decided to use JWT authentication stored in HttpOnly cookies.
Rule: Always validate input requests with Hibernate Validator.
Task: Implement refresh token rotation endpoint and error interceptor in Angular.`;
      this.triggerLiveCompression();
    }

    // Debounced live compression when typing
    this.sub.add(
      this.rawContentSubject.pipe(
        debounceTime(400),
        distinctUntilChanged()
      ).subscribe(() => {
        this.triggerLiveCompression();
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadContext(id: string): void {
    this.isLoading = true;
    this.contextService.getContext(id).subscribe({
      next: (capsule) => {
        this.title = capsule.title;
        this.project = capsule.project;
        this.description = capsule.description || '';
        this.rawContent = capsule.rawContent;
        this.compressedContent = capsule.compressedContent;
        this.compressionStrategy = capsule.compressionStrategy;
        this.tags = capsule.tags || '';
        this.isPublic = capsule.isPublic;
        this.shareSlug = capsule.shareSlug;
        this.version = capsule.version;
        this.originalTokens = capsule.originalTokens;
        this.compressedTokens = capsule.compressedTokens;
        this.compressionRatio = capsule.compressionRatio;
        this.tokensSaved = Math.max(0, this.originalTokens - this.compressedTokens);
        this.isLoading = false;

        this.loadNotes(id);
      },
      error: (err) => {
        this.showToast('Failed to load context');
        this.isLoading = false;
        this.router.navigate(['/']);
      }
    });
  }

  loadNotes(id: string): void {
    this.contextService.getNotes(id).subscribe({
      next: (notes) => this.notes = notes,
      error: (err) => console.error('Failed to load notes', err)
    });
  }

  onRawContentChange(): void {
    this.rawContentSubject.next(this.rawContent);
  }

  getCharRatio(): number {
    if (!this.rawContent || this.rawContent.length === 0) return 0;
    const compLen = this.compressedContent ? this.compressedContent.length : 0;
    const saved = this.rawContent.length - compLen;
    const ratio = (saved / this.rawContent.length) * 100;
    return Math.max(0, Math.round(ratio * 10) / 10);
  }

  checkLmStudioStatus(): void {
    this.contextService.getLmStudioStatus().subscribe({
      next: (res) => {
        this.isLmStudioOnline = res && res.online;
        this.aiProvider = res?.provider || 'LMSTUDIO';
        this.aiModel = res?.model || '';
      },
      error: () => {
        this.isLmStudioOnline = false;
      }
    });
  }

  setStrategy(strategy: 'SEMANTIC_DENSE' | 'STATE_KV' | 'MARKDOWN_OUTLINE' | 'AI_DEEP_DISTILL'): void {
    this.compressionStrategy = strategy;
    this.triggerLiveCompression();
  }

  triggerLiveCompression(): void {
    if (!this.rawContent.trim()) {
      this.compressedContent = '';
      this.originalTokens = 0;
      this.compressedTokens = 0;
      this.compressionRatio = 0;
      this.tokensSaved = 0;
      return;
    }

    this.isCompressing = true;
    this.contextService.previewCompression({
      title: this.title,
      project: this.project,
      rawContent: this.rawContent,
      compressionStrategy: this.compressionStrategy
    }).subscribe({
      next: (res) => {
        this.compressedContent = res.compressedContent;
        this.originalTokens = res.originalTokens;
        this.compressedTokens = res.compressedTokens;
        this.compressionRatio = res.compressionRatio;
        this.tokensSaved = res.tokensSaved;
        this.isCompressing = false;
      },
      error: () => {
        this.isCompressing = false;
      }
    });
  }

  save(): void {
    if (!this.title.trim()) {
      this.showToast('Please enter a context title');
      return;
    }
    if (!this.rawContent.trim()) {
      this.showToast('Please provide raw context content');
      return;
    }

    this.isSaving = true;

    if (this.isEditMode && this.contextId) {
      this.contextService.updateContext(this.contextId, {
        title: this.title,
        project: this.project,
        description: this.description,
        rawContent: this.rawContent,
        compressedContent: this.compressedContent,
        compressionStrategy: this.compressionStrategy,
        tags: this.tags,
        isPublic: this.isPublic
      }).subscribe({
        next: (saved) => {
          this.isSaving = false;
          this.version = saved.version;
          this.shareSlug = saved.shareSlug;
          this.showToast('Context capsule updated successfully!');
        },
        error: () => {
          this.isSaving = false;
          this.showToast('Failed to update context');
        }
      });
    } else {
      this.contextService.createContext({
        title: this.title,
        project: this.project,
        description: this.description,
        rawContent: this.rawContent,
        compressionStrategy: this.compressionStrategy,
        tags: this.tags,
        isPublic: this.isPublic
      }).subscribe({
        next: (created) => {
          this.isSaving = false;
          this.showToast('New context capsule created!');
          this.router.navigate(['/editor', created.id]);
        },
        error: () => {
          this.isSaving = false;
          this.showToast('Failed to create context');
        }
      });
    }
  }

  addNote(): void {
    if (!this.contextId) {
      this.showToast('Save context first before adding notes');
      return;
    }
    if (!this.newNoteContent.trim()) {
      return;
    }

    this.isAddingNote = true;
    const author = this.newNoteAuthor.trim() || 'Collaborator';
    this.contextService.addNote(this.contextId, author, this.newNoteContent.trim()).subscribe({
      next: (note) => {
        this.notes.unshift(note);
        this.newNoteContent = '';
        this.isAddingNote = false;
        this.showToast('Note added to context timeline');
      },
      error: () => {
        this.isAddingNote = false;
        this.showToast('Failed to add note');
      }
    });
  }

  copyPrompt(): void {
    const prompt = `=== OMNICONTEXT ACTIVE MEMORY [${this.title} | ${this.project} | v${this.version}] ===\n${this.compressedContent}\n=== INSTRUCTIONS FOR AI ===\nAbsorb this compressed memory as ground truth for our ongoing work.`;
    navigator.clipboard.writeText(prompt);
    this.showToast('AI Prompt copied to clipboard!');
  }

  copyText(text: string, label: string): void {
    navigator.clipboard.writeText(text);
    this.showToast(`Copied ${label} to clipboard!`);
  }

  getShareUrl(): string {
    return `${window.location.origin}/c/${this.shareSlug}`;
  }

  getRawUrl(): string {
    return this.contextService.getRawUrl(this.shareSlug);
  }

  getMcpUrl(): string {
    return this.contextService.getMcpUrl(this.shareSlug);
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
