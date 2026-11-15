package com.optem.controllers;
import com.optem.database.*;
import com.optem.logic.NotificacionManager;
import java.util.*;
public class GruposMaestroController {
    public String generarCodigoClase(){return UUID.randomUUID().toString().substring(0,6).toUpperCase();}
    public List<Map<String,String>> obtenerTodosLosGrupos(){
        List<Map<String,String>> grupos=MockDB.obtenerTodosLosGrupos();
        for(Map<String,String> m:grupos)m.put("clave",m.get("id_grupo"));
        return grupos;
    }
    public boolean crearGrupo(String materia,String horario){
        if(materia==null||materia.isBlank()||horario==null||horario.isBlank())return false;
        try{MockDB.guardarNuevoGrupo(generarCodigoClase(),materia,"",generarCodigoClase(),horario);return true;}
        catch(RuntimeException e){NotificacionManager.mostrarNotificacion("No se pudo crear",e.getMessage(),"ERROR");return false;}
    }
    public GruposDAO.Alumno agregarAlumnoVerificado(String idGrupo,String busqueda){
        try {return GruposDAO.inscribirAlumno(Integer.parseInt(idGrupo),busqueda);}
        catch(java.sql.SQLException ex){throw new IllegalStateException("No se pudo agregar el alumno: "+ex.getMessage(),ex);}
        catch(NumberFormatException ex){throw new IllegalArgumentException("Identificador de grupo inválido.",ex);}
    }
    public boolean agregarAlumnoManual(String idGrupo,String busqueda){
        try{agregarAlumnoVerificado(idGrupo,busqueda);return true;}
        catch(RuntimeException e){NotificacionManager.mostrarNotificacion("No se pudo inscribir",e.getMessage(),"ERROR");return false;}
    }
}
