import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { ContextService } from '../../services/context.service';
import { ContextCapsule, StatsSummary } from '../../models/context.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  contexts: ContextCapsule[] = [];
  filteredContexts: ContextCapsule[] = [];
  stats: StatsSummary | null = null;

  searchQuery = '';
  selectedProject = 'ALL';
  projects: string[] = [];

  isLoading = true;
  toastMessage = '';
  showShareModal = false;
  selectedShareCapsule: ContextCapsule | null = null;

  constructor(private contextService: ContextService, private router: Router) {}

  private pollSub: any;

  ngOnInit(): void {
    this.loadData();
    // Auto-poll every 3.5 seconds so newly synced contexts from extension/watcher appear in real-time
    this.pollSub = setInterval(() => {
      this.refreshDataSilently();
    }, 3500);
  }

  ngOnDestroy(): void {
    if (this.pollSub) {
      clearInterval(this.pollSub);
    }
  }

  loadData(showLoading = true): void {
    if (showLoading) this.isLoading = true;
    this.contextService.getStats().subscribe({
      next: (s) => {
        this.stats = s;
        this.projects = s.projects || [];
      },
      error: (err) => console.error('Failed to load stats', err)
    });

    this.contextService.getContexts(this.searchQuery, this.selectedProject).subscribe({
      next: (data) => {
        const previousCount = this.contexts.length;
        this.contexts = data;
        this.filterContexts();
        this.isLoading = false;

        // If new capsule arrived via extension/watcher, show a friendly toast
        if (previousCount > 0 && data.length > previousCount) {
          const newest = data[0];
          this.showToast(`⚡ New context capsule synced: "${newest.title}"`);
        }
      },
      error: (err) => {
        console.error('Failed to load contexts', err);
        this.isLoading = false;
      }
    });
  }

  refreshDataSilently(): void {
    this.contextService.getStats().subscribe({
      next: (s) => {
        this.stats = s;
        this.projects = s.projects || [];
      }
    });

    this.contextService.getContexts(this.searchQuery, this.selectedProject).subscribe({
      next: (data) => {
        const previousCount = this.contexts.length;
        if (data.length !== previousCount || JSON.stringify(data.map(d => d.id)) !== JSON.stringify(this.contexts.map(c => c.id))) {
          this.contexts = data;
          this.filterContexts();
          if (previousCount > 0 && data.length > previousCount) {
            this.showToast(`⚡ New context capsule synced from AI: "${data[0].title}"`);
          }
        }
      }
    });
  }

  getCharRatio(raw: string, compressed: string): number {
    if (!raw || raw.length === 0) return 0;
    const compLen = compressed ? compressed.length : 0;
    const saved = raw.length - compLen;
    const ratio = (saved / raw.length) * 100;
    return Math.max(0, Math.round(ratio * 10) / 10);
  }

  filterContexts(): void {
    this.filteredContexts = this.contexts.filter(c => {
      const matchesSearch = !this.searchQuery ||
        c.title.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.project.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        (c.tags && c.tags.toLowerCase().includes(this.searchQuery.toLowerCase()));

      const matchesProject = this.selectedProject === 'ALL' || c.project === this.selectedProject;

      return matchesSearch && matchesProject;
    });
  }

  onSearchChange(): void {
    this.filterContexts();
  }

  selectProject(proj: string): void {
    this.selectedProject = proj;
    this.filterContexts();
  }

  openEditor(id?: string): void {
    if (id) {
      this.router.navigate(['/editor', id]);
    } else {
      this.router.navigate(['/editor']);
    }
  }

  forkContext(capsule: ContextCapsule, event: Event): void {
    event.stopPropagation();
    this.contextService.forkContext(capsule.id).subscribe({
      next: (forked) => {
        this.showToast(`Forked capsule: "${forked.title}"`);
        this.loadData();
      },
      error: (err) => this.showToast('Failed to fork context')
    });
  }

  deleteContext(id: string, title: string, event: Event): void {
    event.stopPropagation();
    if (confirm(`Are you sure you want to delete context "${title}"?`)) {
      this.contextService.deleteContext(id).subscribe({
        next: () => {
          this.showToast(`Deleted "${title}"`);
          this.loadData();
        },
        error: (err) => this.showToast('Failed to delete')
      });
    }
  }

  copyPrompt(capsule: ContextCapsule, event: Event): void {
    event.stopPropagation();
    const prompt = `=== OMNICONTEXT ACTIVE MEMORY [${capsule.title} | ${capsule.project} | v${capsule.version}] ===\n${capsule.compressedContent}\n=== INSTRUCTIONS FOR AI ===\nUse this compressed memory as ground-truth context for our work. Maintain all architectural decisions and constraints.`;
    navigator.clipboard.writeText(prompt);
    this.showToast(`Copied ready-to-inject AI Prompt to clipboard! (${capsule.compressedTokens} tokens)`);
  }

  copyRawLink(capsule: ContextCapsule, event: Event): void {
    event.stopPropagation();
    const url = this.contextService.getRawUrl(capsule.shareSlug);
    navigator.clipboard.writeText(url);
    this.showToast(`Copied AI Raw Fetch URL to clipboard!`);
  }

  openShareModal(capsule: ContextCapsule, event: Event): void {
    event.stopPropagation();
    this.selectedShareCapsule = capsule;
    this.showShareModal = true;
  }

  closeShareModal(): void {
    this.showShareModal = false;
    this.selectedShareCapsule = null;
  }

  copyText(text: string, label: string): void {
    navigator.clipboard.writeText(text);
    this.showToast(`Copied ${label} to clipboard!`);
  }

  getShareUrl(slug: string): string {
    return `${window.location.origin}/c/${slug}`;
  }

  getRawUrl(slug: string): string {
    return this.contextService.getRawUrl(slug);
  }

  getPromptUrl(slug: string): string {
    return this.contextService.getPromptUrl(slug);
  }

  getMcpUrl(slug: string): string {
    return this.contextService.getMcpUrl(slug);
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
