package wails

import "runtime"

type GreetService struct{}

func (g *GreetService) Greet(name string) string {
	return "Hello " + name + "!"
}

func (g *GreetService) GetPlatform() string {
	return runtime.GOOS
}
