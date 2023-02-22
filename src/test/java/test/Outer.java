package test;

class Outer {
    private class Inner {
        public void m() {
            System.out.println(123);
        }
    }

    public Inner getInner() {
        return new Inner();
    }
}
