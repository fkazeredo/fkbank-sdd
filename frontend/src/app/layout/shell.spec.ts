import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Shell } from './shell';

describe('Shell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('renders all six product navigation entries', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const labels = Array.from(
      fixture.nativeElement.querySelectorAll('nav a') as NodeListOf<HTMLAnchorElement>,
    ).map((link) => link.textContent?.trim());

    expect(labels).toEqual(['Account', 'PIX', 'Pay', 'Boxes', 'Card', 'Credit']);
  });

  it('shows no username while the profile has not loaded', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="current-username"]')).toBeNull();
  });

  it('navigation entries are real links, so the shell is keyboard navigable', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('nav a[href]');

    expect(links.length).toBe(6);
  });
});
