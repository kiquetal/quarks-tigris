import { Routes } from '@angular/router';
import { Passphrase } from './passphrase/passphrase';
import { Mp3Upload } from './mp3-upload/mp3-upload';
import { FileList } from './file-list/file-list';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'passphrase', pathMatch: 'full' },
  { path: 'passphrase', component: Passphrase },
  { path: 'upload', component: Mp3Upload, canActivate: [authGuard] },
  { path: 'files', component: FileList, canActivate: [authGuard] },
];
