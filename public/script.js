document.addEventListener('DOMContentLoaded', () => {
    // i18n Logic
    const langSelector = document.getElementById('lang-selector');
    
    const updateContent = (lang) => {
        // Ensure translations object exists
        if (typeof translations === 'undefined') {
            console.error('Translations not loaded');
            return;
        }

        const t = translations[lang] || translations.en;
        if (!t) return;
        
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            const keys = key.split('.');
            let value = t;
            
            keys.forEach(k => {
                if (value && typeof value === 'object') {
                    value = value[k];
                }
            });
            
            if (typeof value === 'string') {
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                    el.placeholder = value;
                } else {
                    el.innerHTML = value;
                }
            }
        });

        // Update document title and lang attribute safely
        try {
            const cleanTitle = t.hero.title.replace(/<[^>]*>/g, ''); // Strip all HTML tags
            document.title = lang === 'en' ? "Airmedy - All-in-one offline music player" : `Airmedy - ${cleanTitle}`;
        } catch (e) {
            console.error('Error updating title:', e);
        }
        document.documentElement.lang = lang;
    };

    const setLanguage = (lang) => {
        try {
            localStorage.setItem('airmedy-lang', lang);
        } catch (e) {
            console.warn('localStorage not available');
        }
        if (langSelector) langSelector.value = lang;
        updateContent(lang);
    };

    if (langSelector) {
        langSelector.addEventListener('change', (e) => {
            setLanguage(e.target.value);
        });
    }

    // Detect browser language or use saved language
    let initialLang = 'en';
    try {
        const savedLang = localStorage.getItem('airmedy-lang');
        const browserLang = navigator.language.split('-')[0];
        const availableTranslations = typeof translations !== 'undefined' ? Object.keys(translations) : [];
        initialLang = savedLang || (availableTranslations.includes(browserLang) ? browserLang : 'en');
    } catch (e) {
        console.warn('Error detecting language, defaulting to English');
    }
    
    setLanguage(initialLang);

    // Theme Toggle Logic
    const themeToggle = document.getElementById('theme-toggle');
    
    const setTheme = (theme) => {
        document.body.classList.remove('light', 'dark');
        document.body.classList.add(theme);
        
        // Update hero mockup image based on theme
        const heroMockup = document.querySelector('.app-window-mockup');
        if (heroMockup) {
            heroMockup.src = `screenshot-${theme}.png`;
        }

        try {
            localStorage.setItem('airmedy-theme', theme);
        } catch (e) {
            console.warn('localStorage not available');
        }
    };

    if (themeToggle) {
        themeToggle.addEventListener('click', () => {
            const currentTheme = document.body.classList.contains('light') ? 'light' : 'dark';
            const newTheme = currentTheme === 'light' ? 'dark' : 'light';
            setTheme(newTheme);
        });
    }

    // Detect saved theme or use dark as default
    let initialTheme = 'dark';
    try {
        const savedTheme = localStorage.getItem('airmedy-theme');
        initialTheme = savedTheme || 'dark';
    } catch (e) {
        console.warn('Error detecting theme preference, defaulting to dark');
    }
    
    setTheme(initialTheme);

    // Mobile Menu Toggle
    const mobileMenuToggle = document.getElementById('mobile-menu-toggle');
    const navLinks = document.querySelector('.nav-links');

    if (mobileMenuToggle) {
        mobileMenuToggle.addEventListener('click', () => {
            document.body.classList.toggle('mobile-menu-open');
        });
    }

    // Close mobile menu when clicking a link
    if (navLinks) {
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                document.body.classList.remove('mobile-menu-open');
            });
        });
    }

    // FAQ Accordion
    const faqItems = document.querySelectorAll('.faq-item');

    faqItems.forEach(item => {
        const question = item.querySelector('.faq-question');
        question.addEventListener('click', () => {
            // Close other items
            faqItems.forEach(otherItem => {
                if (otherItem !== item && otherItem.classList.contains('active')) {
                    otherItem.classList.remove('active');
                }
            });

            // Toggle current item
            item.classList.toggle('active');
        });
    });

    // Smooth scroll for anchor links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const href = this.getAttribute('href');
            if (href === '#') return;
            
            e.preventDefault();
            const target = document.querySelector(href);
            if (target) {
                const headerOffset = 80;
                const elementPosition = target.getBoundingClientRect().top;
                const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

                window.scrollTo({
                    top: offsetPosition,
                    behavior: 'smooth'
                });
            }
        });
    });

    // Reveal animations on scroll
    const revealElements = document.querySelectorAll('.feature-card, .screenshot-mockup-wrapper, .faq-item, .download-card');
    
    const revealOnScroll = () => {
        const triggerBottom = window.innerHeight * 0.8;
        
        revealElements.forEach(el => {
            const elTop = el.getBoundingClientRect().top;
            if (elTop < triggerBottom) {
                el.style.opacity = '1';
                el.style.transform = 'translateY(0)';
            }
        });
    };

    // Initial styles for reveal animation
    revealElements.forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        el.style.transition = 'all 0.6s cubic-bezier(0.4, 0, 0.2, 1)';
    });

    window.addEventListener('scroll', revealOnScroll);
    revealOnScroll(); // Trigger once on load

    // Hero Mockup Tilt & Lighting Effect
    const mockup = document.querySelector('.mockup-wrapper');
    const hero = document.querySelector('.hero');
    const glare = document.querySelector('.mockup-glare');

    if (mockup && hero && glare) {
        hero.addEventListener('mousemove', (e) => {
            const rect = mockup.getBoundingClientRect();
            const margin = 60; // 60px tracking range around the image
            
            const mouseX = e.clientX;
            const mouseY = e.clientY;

            // Check if mouse is within the tracking zone
            const isInside = (
                mouseX >= rect.left - margin &&
                mouseX <= rect.right + margin &&
                mouseY >= rect.top - margin &&
                mouseY <= rect.bottom + margin
            );

            if (!isInside) {
                mockup.style.transform = `rotateY(-10deg) rotateX(5deg) scale(1)`;
                glare.style.opacity = '0';
                return;
            }

            // If inside, show glare and calculate tilt
            glare.style.opacity = '1';
            
            // Mouse position relative to the mockup's center
            const centerX = rect.left + rect.width / 2;
            const centerY = rect.top + rect.height / 2;
            
            // Calculate tilt
            const tiltX = (centerY - mouseY) / 30;
            const tiltY = (mouseX - centerX) / 30;
            
            mockup.style.transform = `rotateX(${tiltX}deg) rotateY(${tiltY}deg) scale(1.02)`;
            
            // Dynamic glare effect (clamped to image bounds)
            let glareX = ((mouseX - rect.left) / rect.width) * 100;
            let glareY = ((mouseY - rect.top) / rect.height) * 100;
            
            glareX = Math.max(-20, Math.min(120, glareX));
            glareY = Math.max(-20, Math.min(120, glareY));

            glare.style.background = `radial-gradient(circle at ${glareX}% ${glareY}%, rgba(255, 255, 255, 0.4) 0%, transparent 60%)`;
        });

        hero.addEventListener('mouseleave', () => {
            mockup.style.transform = `rotateY(-10deg) rotateX(5deg) scale(1)`;
            glare.style.opacity = '0';
            glare.style.background = `radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.15) 0%, transparent 60%)`;
        });
    }
});
