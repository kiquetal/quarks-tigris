import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface MetadataDialogData {
  original_filename: string;
  original_size: number;
  encrypted_size: number;
  algorithm: string;
  version: string;
  verification_status: string;
  timestamp: number;
  kek: string;
  file_id: string;
}

@Component({
  selector: 'app-metadata-dialog',
  imports: [CommonModule],
  templateUrl: './metadata-dialog.html',
  styleUrl: './metadata-dialog.css',
})
export class MetadataDialog {
  @Input() metadata: MetadataDialogData | null = null;
  @Input() isVisible: boolean = false;
  @Output() close = new EventEmitter<void>();

  onClose() {
    this.close.emit();
  }

  onBackdropClick(event: MouseEvent) {
    if (event.target === event.currentTarget) {
      this.onClose();
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }

  formatDate(timestamp: number): string {
    return new Date(timestamp).toLocaleString();
  }

  copyToClipboard(text: string) {
    navigator.clipboard.writeText(text).then(() => {
      alert('Copied to clipboard!');
    }).catch(err => {
      console.error('Failed to copy:', err);
    });
  }
}
