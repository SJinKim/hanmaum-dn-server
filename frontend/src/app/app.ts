import {Component, inject, OnInit, signal} from '@angular/core';
import { RouterOutlet } from '@angular/router';

import {ButtonModule} from 'primeng/button';
import {AuthService} from './core/auth/auth.service';
import {JsonPipe} from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ButtonModule, JsonPipe],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {

  private authService = inject(AuthService);

  userProfile = this.authService.profile;

  login(): void {
    this.authService.login();
  }

  logout(): void {
    this.authService.logout();
  }

}
