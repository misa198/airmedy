# Welcome to Your New Wails3 Project!

Congratulations on generating your Wails3 application! This README will guide you through the next steps to get your project up and running.

## Getting Started

1. Navigate to your project directory in the terminal.

2. To run your application in development mode, use the following command:

   ```
   wails3 dev
   ```

   This will start your application and enable hot-reloading for both frontend and backend changes.

3. To build your application for production, use:

   ```
   wails3 build
   ```

   This will create a production-ready executable in the `build` directory.

## Exploring Wails3 Features

Now that you have your project set up, it's time to explore the features that Wails3 offers:

1. **Check out the examples**: The best way to learn is by example. Visit the `examples` directory in the `v3/examples` directory to see various sample applications.

2. **Run an example**: To run any of the examples, navigate to the example's directory and use:

   ```
   go run .
   ```

   Note: Some examples may be under development during the alpha phase.

3. **Explore the documentation**: Visit the [Wails3 documentation](https://v3.wails.io/) for in-depth guides and API references.

4. **Join the community**: Have questions or want to share your progress? Join the [Wails Discord](https://discord.gg/JDdSxwjhGf) or visit the [Wails discussions on GitHub](https://github.com/wailsapp/wails/discussions).

## Project Structure

Airmedy follows a clean, modular architecture. For a comprehensive overview of the directory structure and file responsibilities, see [FILES.md](./FILES.md).

- `internal/domain`: Core business logic, models, and interfaces (Hexagonal Core).
- `internal/infra`: Concrete implementations for database, search, and metadata (Adapters).
- `internal/app`: Application services and dependency injection.
- `frontend/`: Vue.js 3 user interface with ShadCN-vue and Pinia.
- `build/`: Platform-specific configuration and assets.
- `main.go`: Application entry point.

## Next Steps

1. **Development Roadmap:** Refer to [PLAN.md](./PLAN.md) for the current project status and upcoming features.
2. **Project Mandates:** See [GEMINI.md](./GEMINI.md) for technical standards and development guidelines.
3. **Technical Architecture:** Review [TECHDOC.md](./TECHDOC.md) for an exhaustive look into DB schemas and metadata logic.
4. **Design System & Layout:** Check [THEME.md](./THEME.md) for styling and [Wireframe.md](./Wireframe.md) for user flow diagrams.
5. **Development:** Use `wails3 dev` to start the application with hot-reloading.

Happy coding with Wails3! If you encounter any issues or have questions, don't hesitate to consult the documentation or reach out to the Wails community.
