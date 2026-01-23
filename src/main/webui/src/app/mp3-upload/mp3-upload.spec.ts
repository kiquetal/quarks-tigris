import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Mp3Upload } from './mp3-upload';

describe('Mp3Upload', () => {
  let component: Mp3Upload;
  let fixture: ComponentFixture<Mp3Upload>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Mp3Upload]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Mp3Upload);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
