import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { ContextEditorComponent } from './pages/editor/context-editor.component';
import { SharedContextComponent } from './pages/shared-context/shared-context.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'editor', component: ContextEditorComponent },
  { path: 'editor/:id', component: ContextEditorComponent },
  { path: 'c/:slug', component: SharedContextComponent },
  { path: '**', redirectTo: '' }
];
