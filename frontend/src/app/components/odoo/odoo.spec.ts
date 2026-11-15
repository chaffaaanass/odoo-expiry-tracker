import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Odoo } from './odoo';

describe('Odoo', () => {
  let component: Odoo;
  let fixture: ComponentFixture<Odoo>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Odoo],
    }).compileComponents();

    fixture = TestBed.createComponent(Odoo);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
